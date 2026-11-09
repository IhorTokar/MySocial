package com.example.social.server.service;

import com.example.social.server.entity.AuditAction;
import com.example.social.server.exception.NotFoundException;
import com.example.social.server.repository.EchoHistoryRepository;
import com.example.social.server.repository.EchoHistoryRepository.CommunityRow;
import com.example.social.server.service.CommunityAnalysisService.AnalyzedCommunity;
import com.example.social.shared.dto.CommunityAnalysisDto;
import com.example.social.shared.dto.EchoSnapshotDetailDto;
import com.example.social.shared.dto.EchoSnapshotDto;
import com.example.social.shared.dto.UserEchoPointDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Історія ехо-камер: знімки аналізу спільнот у часі, зіставлення спільнот між знімками
 * і траєкторія окремого користувача.
 */
@Service
public class EchoHistoryService {

    private static final Logger log = LoggerFactory.getLogger(EchoHistoryService.class);

    /** Менший збіг складу (за Жаккаром) вважається вже іншою спільнотою. */
    static final double MIN_OVERLAP = 0.3;
    private static final double SAME_SCORE_EPS = 1e-9;

    /** Підсумок знімка по всій мережі. */
    record Summary(int communityCount, int userCount, double weightedEchoScore, double maxEchoScore) {
    }

    private final CommunityAnalysisService communityAnalysisService;
    private final CommunityDetectionService communityDetectionService;
    private final GraphSyncService graphSyncService;
    private final EchoHistoryRepository repository;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public EchoHistoryService(CommunityAnalysisService communityAnalysisService,
                              CommunityDetectionService communityDetectionService,
                              GraphSyncService graphSyncService,
                              EchoHistoryRepository repository,
                              AuditService auditService,
                              TransactionTemplate transactionTemplate) {
        this.communityAnalysisService = communityAnalysisService;
        this.communityDetectionService = communityDetectionService;
        this.graphSyncService = graphSyncService;
        this.repository = repository;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Знімок за запитом адміністратора.
     *
     * @param refresh спершу синхронізувати граф з Postgres і заново визначити спільноти,
     *                щоб знімок відображав поточні дані, а не результат останнього ручного запуску
     */
    public EchoSnapshotDto takeSnapshot(boolean leiden, boolean refresh, long adminId) {
        return snapshot(leiden, refresh, adminId, false).orElseThrow();
    }

    /** Плановий знімок: завжди зі свіжими спільнотами; якщо нічого не змінилось — не зберігається. */
    public Optional<EchoSnapshotDto> takeScheduledSnapshot(boolean leiden) {
        return snapshot(leiden, true, null, true);
    }

    private Optional<EchoSnapshotDto> snapshot(boolean leiden, boolean refresh, Long adminId, boolean skipUnchanged) {
        if (refresh) {
            // Neo4j поза транзакцією Postgres — тому до TransactionTemplate
            graphSyncService.syncAll();
            if (leiden) {
                communityDetectionService.detectCommunitiesLeiden();
            } else {
                communityDetectionService.detectCommunities();
            }
        }
        String algorithm = algorithm(leiden);

        // аналіз і запис — однією транзакцією: або знімок повністю, або нічого
        return transactionTemplate.execute(status -> {
            List<AnalyzedCommunity> communities = communityAnalysisService.analyzeCommunitiesWithMembers(leiden);
            Summary summary = summarize(communities.stream().map(AnalyzedCommunity::analysis).toList());

            if (skipUnchanged && repository.findLatest(algorithm).filter(last -> sameAs(last, summary)).isPresent()) {
                log.info("Echo-history {}: без змін з попереднього знімка — не зберігаю", algorithm);
                return Optional.<EchoSnapshotDto>empty();
            }

            long snapshotId = repository.insertSnapshot(algorithm, adminId == null ? "scheduled" : "manual", adminId,
                    summary.communityCount(), summary.userCount(), summary.weightedEchoScore(), summary.maxEchoScore());
            repository.insertCommunities(snapshotId, communities.stream().map(EchoHistoryService::toRow).toList());

            if (adminId != null) {
                auditService.log(adminId, AuditAction.ECHO_SNAPSHOT, "echo_snapshot", snapshotId,
                        String.format(Locale.ROOT, "%s: спільнот — %d, користувачів — %d, зважений echo-score %.4f",
                                algorithm, summary.communityCount(), summary.userCount(), summary.weightedEchoScore()));
            }
            log.info("Echo-history {}: знімок #{} ({} спільнот, echo {})",
                    algorithm, snapshotId, summary.communityCount(), summary.weightedEchoScore());
            return repository.findSnapshot(snapshotId);
        });
    }

    @Transactional(readOnly = true)
    public List<EchoSnapshotDto> history(boolean leiden, int limit) {
        return repository.findSnapshots(algorithm(leiden), limit);
    }

    /** Знімок зі спільнотами; кожна зіставлена зі «своєю» спільнотою попереднього знімка. */
    @Transactional(readOnly = true)
    public EchoSnapshotDetailDto snapshotDetail(long snapshotId) {
        EchoSnapshotDto snapshot = repository.findSnapshot(snapshotId)
                .orElseThrow(() -> new NotFoundException("Знімок #" + snapshotId + " не знайдено"));
        Optional<EchoSnapshotDto> previous = repository.findPrevious(snapshot.algorithm(), snapshotId);

        Map<Long, CommunityRow> previousRows = previous
                .map(p -> repository.findCommunities(p.snapshotId()).stream()
                        .collect(Collectors.toMap(CommunityRow::community, Function.identity())))
                .orElse(Map.of());
        Map<Long, List<Long>> previousMembers = previousRows.values().stream()
                .collect(Collectors.toMap(CommunityRow::community, CommunityRow::memberIds));

        List<EchoSnapshotDetailDto.Community> communities = repository.findCommunities(snapshotId).stream()
                .map(row -> {
                    Optional<CommunityMatcher.Match> match =
                            CommunityMatcher.bestMatch(row.memberIds(), previousMembers, MIN_OVERLAP);
                    return new EchoSnapshotDetailDto.Community(
                            row.community(), row.memberCount(), row.postCount(),
                            row.topicHomogeneity(), row.semanticCohesion(),
                            row.avgSentiment(), row.sentimentHomogeneity(), row.echoChamberScore(),
                            match.map(CommunityMatcher.Match::community).orElse(null),
                            match.map(CommunityMatcher.Match::jaccard).orElse(null),
                            match.map(m -> row.echoChamberScore() - previousRows.get(m.community()).echoChamberScore())
                                    .orElse(null));
                })
                .toList();

        return new EchoSnapshotDetailDto(snapshot, previous.map(EchoSnapshotDto::snapshotId).orElse(null), communities);
    }

    @Transactional(readOnly = true)
    public List<UserEchoPointDto> userHistory(long userId, boolean leiden, int limit) {
        return repository.findUserTrajectory(userId, algorithm(leiden), limit);
    }

    /**
     * Кількість спільнот і учасників, середній echo-score зважений за розміром спільноти, максимум.
     * Спільноти без постів у середнє не входять: для них показник не визначений (0 означає «немає даних»),
     * і вони штучно занижували б результат.
     */
    static Summary summarize(List<CommunityAnalysisDto> communities) {
        int users = 0;
        int scoredUsers = 0;
        double weightedSum = 0;
        double max = 0;
        for (CommunityAnalysisDto c : communities) {
            users += c.getMemberCount();
            if (c.getPostCount() == 0) {
                continue;
            }
            scoredUsers += c.getMemberCount();
            weightedSum += c.getMemberCount() * c.getEchoChamberScore();
            max = Math.max(max, c.getEchoChamberScore());
        }
        return new Summary(communities.size(), users, scoredUsers > 0 ? weightedSum / scoredUsers : 0.0, max);
    }

    private static boolean sameAs(EchoSnapshotDto last, Summary current) {
        return last.communityCount() == current.communityCount()
                && last.userCount() == current.userCount()
                && Math.abs(last.weightedEchoScore() - current.weightedEchoScore()) < SAME_SCORE_EPS
                && Math.abs(last.maxEchoScore() - current.maxEchoScore()) < SAME_SCORE_EPS;
    }

    private static CommunityRow toRow(AnalyzedCommunity c) {
        CommunityAnalysisDto a = c.analysis();
        return new CommunityRow(a.getCommunity(), a.getMemberCount(), a.getPostCount(),
                a.getTopicHomogeneity(), a.getSemanticCohesion(),
                a.getAvgSentiment(), a.getSentimentHomogeneity(), a.getEchoChamberScore(),
                c.memberIds());
    }

    private static String algorithm(boolean leiden) {
        return leiden ? "leiden" : "louvain";
    }
}