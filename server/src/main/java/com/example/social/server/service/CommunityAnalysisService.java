package com.example.social.server.service;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.Tag;
import com.example.social.server.entity.User;
import com.example.social.server.repository.PostEmbeddingRepository;
import com.example.social.server.repository.PostRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.shared.dto.CommunityAnalysisDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommunityAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(CommunityAnalysisService.class);

    // мінімальний розмір спільноти, щоб мати сенс для аналізу однорідності
    private static final int MIN_COMMUNITY_SIZE = 2;
    // обмеження постів на community для попарної семантичної схожості — O(n^2) по SQL,
    // для навчального проєкту достатньо репрезентативної вибірки
    private static final int MAX_POSTS_FOR_COHESION = 60;
    private static final int TOP_TAGS_LIMIT = 5;

    private final CommunityDetectionService communityDetectionService;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostEmbeddingRepository postEmbeddingRepository;
    private final SentimentService sentimentService;

    public CommunityAnalysisService(CommunityDetectionService communityDetectionService,
                                    UserRepository userRepository,
                                    PostRepository postRepository,
                                    PostEmbeddingRepository postEmbeddingRepository,
                                    SentimentService sentimentService) {
        this.communityDetectionService = communityDetectionService;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.postEmbeddingRepository = postEmbeddingRepository;
        this.sentimentService = sentimentService;
    }

    @Transactional(readOnly = true)
    public List<CommunityAnalysisDto> analyzeCommunities(boolean useLeiden) {
        List<Map<String, Object>> communityRows = useLeiden
                ? communityDetectionService.getCommunitiesLeiden()
                : communityDetectionService.getCommunities();
        if (communityRows.isEmpty()) {
            throw new IllegalStateException("Спільноти ще не визначені — спершу викличте /api/graph/communities");
        }

        Map<Long, List<Long>> userIdsByCommunity = new HashMap<>();
        for (Map<String, Object> row : communityRows) {
            Long community = (Long) row.get("community");
            Long userId = (Long) row.get("userId");
            userIdsByCommunity.computeIfAbsent(community, k -> new ArrayList<>()).add(userId);
        }

        List<CommunityAnalysisDto> results = new ArrayList<>();

        for (Map.Entry<Long, List<Long>> entry : userIdsByCommunity.entrySet()) {
            Long community = entry.getKey();
            List<Long> userIds = entry.getValue();

            if (userIds.size() < MIN_COMMUNITY_SIZE) {
                continue;
            }

            CommunityAnalysisDto dto = analyzeSingleCommunity(community, userIds);
            results.add(dto);
        }

        results.sort(Comparator.comparingDouble(CommunityAnalysisDto::getEchoChamberScore).reversed());

        log.info("Analyzed {} communities (min size {})", results.size(), MIN_COMMUNITY_SIZE);
        return results;
    }

    public CommunityAnalysisDto analyzeSingleCommunity(Long community, List<Long> userIds) {
        List<User> users = userRepository.findAllById(userIds);
        List<Post> posts = postRepository.findByUserInOrderByCreatedDateDesc(users);

        if (posts.isEmpty()) {
            return new CommunityAnalysisDto(community, users.size(), 0,
                    List.of(), 0.0, null, 0.0, 0.0, 0.0);
        }

        // --- 1. Тематична однорідність: ентропія розподілу тегів ---
        Map<String, Long> tagCounts = posts.stream()
                .flatMap(p -> p.getTags().stream())
                .map(Tag::getName)
                .collect(Collectors.groupingBy(name -> name, Collectors.counting()));

        double topicHomogeneity = computeTopicHomogeneity(tagCounts);

        List<CommunityAnalysisDto.TagCount> topTags = tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_TAGS_LIMIT)
                .map(e -> new CommunityAnalysisDto.TagCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        // --- 2. Семантична згуртованість: середня попарна косинусна близькість ---
        List<Long> postIdsForCohesion = posts.stream()
                .map(Post::getPostId)
                .limit(MAX_POSTS_FOR_COHESION)
                .collect(Collectors.toList());
        Double semanticCohesion = postEmbeddingRepository.findAveragePairwiseSimilarity(postIdsForCohesion);

        // --- 3. Емоційна однорідність: середній сентимент і його стабільність ---
        List<Double> sentiments = posts.stream()
                .map(p -> sentimentService.analyze(p.getText()))
                .collect(Collectors.toList());

        double avgSentiment = sentiments.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double sentimentStdDev = computeStdDev(sentiments, avgSentiment);
        // stdDev у сентименті лежить приблизно в [0, 1] (діапазон значень [-1,1]) —
        // однорідність = 1 - stdDev, чим менший розкид тональності, тим вища однорідність
        double sentimentHomogeneity = Math.max(0.0, 1.0 - sentimentStdDev);

        // --- Зведений показник "ехо-камери" ---
        double cohesionForScore = semanticCohesion != null ? semanticCohesion : 0.0;
        double echoChamberScore = (topicHomogeneity + cohesionForScore + sentimentHomogeneity) / 3.0;

        return new CommunityAnalysisDto(
                community, userIds.size(), posts.size(), topTags,
                topicHomogeneity, semanticCohesion, avgSentiment,
                sentimentHomogeneity, echoChamberScore
        );
    }

    /** Ехо-спільнота конкретного користувача, або Optional.empty(), якщо ще не визначена чи закоротка. */
    @Transactional(readOnly = true)
    public Optional<CommunityAnalysisDto> getCommunityForUser(Long userId, boolean useLeiden) {
        List<Map<String, Object>> communityRows = useLeiden
                ? communityDetectionService.getCommunitiesLeiden()
                : communityDetectionService.getCommunities();

        Map<Long, List<Long>> userIdsByCommunity = new HashMap<>();
        Long myCommunity = null;

        for (Map<String, Object> row : communityRows) {
            Long community = (Long) row.get("community");
            Long rowUserId = (Long) row.get("userId");
            userIdsByCommunity.computeIfAbsent(community, k -> new ArrayList<>()).add(rowUserId);
            if (rowUserId.equals(userId)) {
                myCommunity = community;
            }
        }

        if (myCommunity == null) {
            return Optional.empty();
        }

        List<Long> members = userIdsByCommunity.get(myCommunity);
        if (members.size() < MIN_COMMUNITY_SIZE) {
            return Optional.empty();
        }

        return Optional.of(analyzeSingleCommunity(myCommunity, members));
    }

    /**
     * Нормалізована однорідність розподілу тегів: 1 - (ентропія Шеннона / максимальна можлива ентропія).
     * 1.0 = усі пости про одну тему (одна ідеальна ехо-камера), 0.0 = теги розподілені рівномірно.
     */
    private double computeTopicHomogeneity(Map<String, Long> tagCounts) {
        if (tagCounts.isEmpty()) {
            return 0.0;
        }
        if (tagCounts.size() == 1) {
            return 1.0;
        }

        long total = tagCounts.values().stream().mapToLong(Long::longValue).sum();
        double entropy = 0.0;
        for (long count : tagCounts.values()) {
            double p = (double) count / total;
            entropy -= p * (Math.log(p) / Math.log(2));
        }

        double maxEntropy = Math.log(tagCounts.size()) / Math.log(2);
        double normalizedEntropy = maxEntropy > 0 ? entropy / maxEntropy : 0.0;

        return 1.0 - normalizedEntropy;
    }

    private double computeStdDev(List<Double> values, double mean) {
        if (values.size() < 2) {
            return 0.0;
        }
        double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }
}