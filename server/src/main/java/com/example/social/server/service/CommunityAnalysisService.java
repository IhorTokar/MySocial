package com.example.social.server.service;

import com.example.social.server.exception.*;

import com.example.social.server.entity.Post;
import com.example.social.server.entity.Tag;
import com.example.social.server.entity.User;
import com.example.social.server.repository.PostEmbeddingRepository;
import com.example.social.server.repository.PostRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.shared.dto.CommunityAnalysisDto;
import com.example.social.shared.dto.CommunityMemberDto;
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
    /**
     * Тональність ближче до нуля, ніж цей поріг, вважається нейтральною. Модель повертає
     * клас × впевненість (нейтральний клас — рівно 0, інші — щонайменше ≈ 0.33 за модулем),
     * тож поріг потрібен лише для словникового запасного варіанта, що дає проміжні значення.
     */
    static final double NEUTRAL_BAND = 0.1;

    private final CommunityDetectionService communityDetectionService;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostEmbeddingRepository postEmbeddingRepository;
    private final PostSentimentService postSentimentService;

    public CommunityAnalysisService(CommunityDetectionService communityDetectionService,
                                    UserRepository userRepository,
                                    PostRepository postRepository,
                                    PostEmbeddingRepository postEmbeddingRepository,
                                    PostSentimentService postSentimentService) {
        this.communityDetectionService = communityDetectionService;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.postEmbeddingRepository = postEmbeddingRepository;
        this.postSentimentService = postSentimentService;
    }

    /** Результат аналізу спільноти разом з її складом — для історії ехо-камер. */
    public record AnalyzedCommunity(CommunityAnalysisDto analysis, List<Long> memberIds) {
    }

    @Transactional(readOnly = true)
    public List<CommunityAnalysisDto> analyzeCommunities(boolean useLeiden) {
        return analyzeCommunitiesWithMembers(useLeiden).stream()
                .map(AnalyzedCommunity::analysis)
                .collect(Collectors.toList());
    }

    /** Те саме, що analyzeCommunities, але кожна спільнота — разом зі списком учасників. */
    @Transactional(readOnly = true)
    public List<AnalyzedCommunity> analyzeCommunitiesWithMembers(boolean useLeiden) {
        List<CommunityMemberDto> communityRows = useLeiden
                ? communityDetectionService.getCommunitiesLeiden()
                : communityDetectionService.getCommunities();
        if (communityRows.isEmpty()) {
            throw new ConflictException("Спільноти ще не визначені — спершу викличте /api/graph/communities");
        }

        Map<Long, List<Long>> userIdsByCommunity = new HashMap<>();
        for (CommunityMemberDto row : communityRows) {
            userIdsByCommunity.computeIfAbsent(row.community(), k -> new ArrayList<>()).add(row.userId());
        }

        List<AnalyzedCommunity> results = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : userIdsByCommunity.entrySet()) {
            List<Long> userIds = entry.getValue();
            if (userIds.size() < MIN_COMMUNITY_SIZE) {
                continue;
            }
            results.add(new AnalyzedCommunity(analyzeSingleCommunity(entry.getKey(), userIds), userIds));
        }

        results.sort(Comparator.comparingDouble((AnalyzedCommunity c) -> c.analysis().getEchoChamberScore()).reversed());

        log.info("Analyzed {} communities (min size {})", results.size(), MIN_COMMUNITY_SIZE);
        return results;
    }

    @Transactional(readOnly = true)
    public CommunityAnalysisDto analyzeUsers(long label, List<Long> userIds) {
        return analyzeSingleCommunity(label, userIds);
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

        // Тональність береться з БД; для старих постів без неї — дораховується й зберігається один раз
        Map<Long, Double> computedNow = postSentimentService.fillMissing(posts.stream()
                .filter(p -> p.getSentiment() == null)
                .map(Post::getPostId)
                .toList());
        List<Double> sentiments = posts.stream()
                .map(p -> p.getSentiment() != null ? p.getSentiment() : computedNow.get(p.getPostId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        double avgSentiment = sentiments.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        // --- 3. Однорідність тональності: ентропія розподілу полярностей (як і для тем) ---
        double sentimentHomogeneity = computeSentimentHomogeneity(sentiments);

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
        List<CommunityMemberDto> communityRows = useLeiden
                ? communityDetectionService.getCommunitiesLeiden()
                : communityDetectionService.getCommunities();

        Map<Long, List<Long>> userIdsByCommunity = new HashMap<>();
        Long myCommunity = null;

        for (CommunityMemberDto row : communityRows) {
            Long community = row.community();
            Long rowUserId = row.userId();
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
    static double computeTopicHomogeneity(Map<String, Long> tagCounts) {
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

    /**
     * Однорідність тональності: 1 - (ентропія розподілу полярностей / log 3).
     * Кожен пост — позитивний, нейтральний або негативний (за знаком тональності з порогом
     * {@link #NEUTRAL_BAND}); 1.0 = усі пости однієї полярності, 0.0 = три полярності порівну.
     *
     * <p>На відміну від тем, нормування завжди на 3 класи: полярностей рівно три, незалежно від того,
     * скільки з них трапилось у спільноті. Формула та сама, що й для тематичної однорідності, тож обидві
     * складові echo-score вимірюють одне — наскільки розподіл зосереджений на одному значенні.
     */
    static double computeSentimentHomogeneity(List<Double> sentiments) {
        if (sentiments.isEmpty()) {
            return 0.0;
        }
        long[] counts = new long[3];   // негатив, нейтраль, позитив
        for (double s : sentiments) {
            counts[polarity(s) + 1]++;
        }
        double entropy = 0.0;
        for (long count : counts) {
            if (count > 0) {
                double p = (double) count / sentiments.size();
                entropy -= p * Math.log(p);
            }
        }
        return 1.0 - entropy / Math.log(3);
    }

    /** -1 — негатив, 0 — нейтраль, 1 — позитив. */
    static int polarity(double sentiment) {
        if (sentiment > NEUTRAL_BAND) {
            return 1;
        }
        return sentiment < -NEUTRAL_BAND ? -1 : 0;
    }
}