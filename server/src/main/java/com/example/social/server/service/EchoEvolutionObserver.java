package com.example.social.server.service;

import com.example.social.shared.dto.CommunityMemberDto;
import com.example.social.shared.dto.EchoSnapshotDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Зв'язок еволюції мережі з рештою системи: після кожного тижня — вектори й тональність нових постів
 * (NLP-сервіс) і знімок ехо-камер (Neo4j + Louvain); траєкторії й склад спільнот — з історії.
 */
@Component
public class EchoEvolutionObserver implements SeedEvolutionService.Observer {

    private static final Logger log = LoggerFactory.getLogger(EchoEvolutionObserver.class);

    private final TestDataService testDataService;
    private final PostSentimentService postSentimentService;
    private final EchoHistoryService echoHistoryService;
    private final CommunityDetectionService communityDetectionService;

    public EchoEvolutionObserver(TestDataService testDataService, PostSentimentService postSentimentService,
                                 EchoHistoryService echoHistoryService,
                                 CommunityDetectionService communityDetectionService) {
        this.testDataService = testDataService;
        this.postSentimentService = postSentimentService;
        this.echoHistoryService = echoHistoryService;
        this.communityDetectionService = communityDetectionService;
    }

    @Override
    public SeedEvolutionService.SnapshotInfo afterEpoch(int epoch, long adminId) {
        // без NLP знімок однаково можливий: семантика буде порожня, тональність — зі словника
        try {
            Map<String, Object> embeddings = testDataService.backfillEmbeddings();
            log.info("Еволюція, тиждень {}: вектори {}", epoch, embeddings);
        } catch (RuntimeException e) {
            log.warn("Еволюція, тиждень {}: вектори не пораховано — {}", epoch, e.getMessage());
        }
        postSentimentService.backfill(false);

        EchoSnapshotDto s = echoHistoryService.takeSnapshot(false, true, adminId);
        return new SeedEvolutionService.SnapshotInfo(s.snapshotId(), s.communityCount(), s.userCount(),
                s.weightedEchoScore(), s.maxEchoScore());
    }

    @Override
    public List<SeedEvolutionService.Point> trajectory(long userId) {
        return echoHistoryService.userHistory(userId, false, 1000).stream()
                .map(p -> new SeedEvolutionService.Point(p.snapshotId(), p.community(), p.memberCount(),
                        p.echoChamberScore()))
                .toList();
    }

    @Override
    public Map<Long, Long> communities() {
        Map<Long, Long> result = new HashMap<>();
        for (CommunityMemberDto row : communityDetectionService.getCommunities()) {
            result.put(row.userId(), row.community());
        }
        return result;
    }
}