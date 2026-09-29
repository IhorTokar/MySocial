package com.example.social.server.service;

import com.example.social.server.entity.Comment;
import com.example.social.server.entity.Post;
import com.example.social.server.entity.PostLike;
import com.example.social.server.entity.Tag;
import com.example.social.server.entity.User;
import com.example.social.server.repository.CommentRepository;
import com.example.social.server.repository.FollowersRepository;
import com.example.social.server.repository.PostEmbeddingRepository;
import com.example.social.server.repository.PostLikeRepository;
import com.example.social.server.repository.PostRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.shared.dto.PostResponseDto;
import com.example.social.shared.dto.RecommendationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private static final double POPULARITY_WEIGHT = 0.20;
    private static final double ITEM_CF_WEIGHT = 0.25;
    private static final double GRAPH_SIM_WEIGHT = 0.20;
    private static final double SEMANTIC_WEIGHT = 0.20;
    private static final double RECENCY_WEIGHT = 0.15;
    private static final double FOLLOWED_BONUS = 0.3;

    private static final double LIKE_REACTION_WEIGHT = 1.0;
    private static final double COMMENT_REACTION_WEIGHT = 1.5;

    private static final int POPULARITY_WINDOW_DAYS = 7;
    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_CO_REACTORS = 20;
    private static final int SIMILAR_USERS_TOP_N = 10;

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;
    private final FollowersRepository followersRepository;
    private final UserRepository userRepository;
    private final UserSimilarityService userSimilarityService;
    private final PostEmbeddingRepository postEmbeddingRepository;
    private final PostService postService;

    public RecommendationService(PostRepository postRepository,
                                 PostLikeRepository postLikeRepository,
                                 CommentRepository commentRepository,
                                 FollowersRepository followersRepository,
                                 UserRepository userRepository,
                                 UserSimilarityService userSimilarityService,
                                 PostEmbeddingRepository postEmbeddingRepository,
                                 PostService postService) {
        this.postRepository = postRepository;
        this.postLikeRepository = postLikeRepository;
        this.commentRepository = commentRepository;
        this.followersRepository = followersRepository;
        this.userRepository = userRepository;
        this.userSimilarityService = userSimilarityService;
        this.postEmbeddingRepository = postEmbeddingRepository;
        this.postService = postService;
    }

    @Transactional(readOnly = true)
    public List<PostResponseDto> getHybridFeed(Long currentUserId, int limit) {
        List<Post> posts = computeScoredFeed(currentUserId, limit).stream()
                .map(ScoredPost::post)
                .collect(Collectors.toList());
        return postService.toDtos(posts, currentUserId);
    }

    @Transactional(readOnly = true)
    public List<PostResponseDto> getHybridFeed(Long currentUserId) {
        return getHybridFeed(currentUserId, DEFAULT_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<RecommendationDto> getHybridFeedDebug(Long currentUserId, int limit) {
        return computeScoredFeed(currentUserId, limit).stream()
                .map(this::toDebugDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RecommendationDto> getHybridFeedDebug(Long currentUserId) {
        return getHybridFeedDebug(currentUserId, DEFAULT_LIMIT);
    }

    private List<ScoredPost> computeScoredFeed(Long currentUserId, int limit) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + currentUserId));

        LocalDateTime since = LocalDateTime.now().minusDays(POPULARITY_WINDOW_DAYS);

        Map<Long, Long> popularityByPostId = postLikeRepository.countLikesSince(since).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        Map<Long, Double> itemCfScoreByPostId = computeItemBasedScores(currentUser);
        Map<Long, Double> graphSimScoreByPostId = computeGraphSimilarityScores(currentUserId, currentUser);

        Set<Long> followedUserIds = followersRepository.findByFollower(currentUser).stream()
                .map(f -> f.getFollowing().getUserId())
                .collect(Collectors.toSet());

        Set<Long> candidatePostIds = new HashSet<>();
        candidatePostIds.addAll(popularityByPostId.keySet());
        candidatePostIds.addAll(itemCfScoreByPostId.keySet());
        candidatePostIds.addAll(graphSimScoreByPostId.keySet());

        if (!followedUserIds.isEmpty()) {
            List<User> followed = userRepository.findAllById(followedUserIds);
            postRepository.findByUserInOrderByCreatedDateDesc(followed)
                    .forEach(p -> candidatePostIds.add(p.getPostId()));
        }

        List<Post> candidates;
        boolean coldStart = candidatePostIds.isEmpty();
        if (coldStart) {
            candidates = postRepository.findTop50ByOrderByCreatedDateDesc();
        } else {
            candidates = postRepository.findAllById(candidatePostIds);
        }

        Map<Long, Double> semanticScoreByPostId = computeSemanticScores(currentUser, candidates);

        double maxPopularity = popularityByPostId.values().stream().mapToLong(Long::longValue).max().orElse(1L);
        double maxItemCf = itemCfScoreByPostId.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double maxGraphSim = graphSimScoreByPostId.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double maxSemantic = semanticScoreByPostId.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

        List<ScoredPost> scored = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Post post : candidates) {
            long likesInWindow = popularityByPostId.getOrDefault(post.getPostId(), 0L);
            double popularityScore = maxPopularity > 0 ? likesInWindow / maxPopularity : 0.0;

            double rawItemCf = itemCfScoreByPostId.getOrDefault(post.getPostId(), 0.0);
            double itemCfScore = maxItemCf > 0 ? rawItemCf / maxItemCf : 0.0;

            double rawGraphSim = graphSimScoreByPostId.getOrDefault(post.getPostId(), 0.0);
            double graphSimScore = maxGraphSim > 0 ? rawGraphSim / maxGraphSim : 0.0;

            double rawSemantic = semanticScoreByPostId.getOrDefault(post.getPostId(), 0.0);
            double semanticScore = maxSemantic > 0 ? rawSemantic / maxSemantic : 0.0;

            boolean isFollowed = followedUserIds.contains(post.getUser().getUserId());

            long hoursOld = Duration.between(post.getCreatedDate(), now).toHours();
            double recencyScore = 1.0 / (1.0 + hoursOld / 24.0);

            double followedBonus = isFollowed ? FOLLOWED_BONUS : 0.0;

            double totalScore = POPULARITY_WEIGHT * popularityScore
                    + ITEM_CF_WEIGHT * itemCfScore
                    + GRAPH_SIM_WEIGHT * graphSimScore
                    + SEMANTIC_WEIGHT * semanticScore
                    + RECENCY_WEIGHT * recencyScore
                    + followedBonus;

            ScoreBreakdown breakdown = new ScoreBreakdown(
                    likesInWindow, popularityScore, itemCfScore, graphSimScore, semanticScore,
                    recencyScore, hoursOld, isFollowed, followedBonus, totalScore);

            if (log.isDebugEnabled()) {
                log.debug(String.format(
                        "[feed uid=%d] post=%d author=%s likes=%d pop=%.3f itemCf=%.3f graphSim=%.3f semantic=%.3f rec=%.3f(%dh) followed=%b TOTAL=%.3f",
                        currentUserId, post.getPostId(), post.getUser().getUsername(),
                        likesInWindow, popularityScore, itemCfScore, graphSimScore, semanticScore,
                        recencyScore, hoursOld, isFollowed, totalScore));
            }

            scored.add(new ScoredPost(post, totalScore, breakdown));
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredPost::score).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private Map<Long, Double> computeSemanticScores(User currentUser, List<Post> candidates) {
        List<Long> seedPostIds = new ArrayList<>();
        postLikeRepository.findByUser(currentUser).forEach(l -> seedPostIds.add(l.getPost().getPostId()));
        commentRepository.findByUser(currentUser).forEach(c -> seedPostIds.add(c.getPost().getPostId()));

        if (seedPostIds.isEmpty() || candidates.isEmpty()) {
            return Map.of();
        }

        List<Long> candidateIds = candidates.stream().map(Post::getPostId).collect(Collectors.toList());
        return postEmbeddingRepository.findAverageSemanticSimilarity(candidateIds, seedPostIds);
    }

    private Map<Long, Double> computeGraphSimilarityScores(Long currentUserId, User currentUser) {
        List<Map<String, Object>> similarUsers;
        try {
            similarUsers = userSimilarityService.getSimilarUsers(currentUserId, SIMILAR_USERS_TOP_N, false);
        } catch (IllegalArgumentException e) {
            log.debug("[feed uid={}] graph similarity skipped: {}", currentUserId, e.getMessage());
            return Map.of();
        }

        if (similarUsers.isEmpty()) {
            return Map.of();
        }

        Map<Long, Double> similarityByUserId = new HashMap<>();
        for (Map<String, Object> entry : similarUsers) {
            similarityByUserId.put((Long) entry.get("userId"), (Double) entry.get("similarityScore"));
        }

        List<User> similarUserEntities = userRepository.findAllById(similarityByUserId.keySet());
        Map<Long, Double> scoreByPostId = new HashMap<>();

        for (PostLike like : postLikeRepository.findByUserIn(similarUserEntities)) {
            Long postId = like.getPost().getPostId();
            double similarity = similarityByUserId.getOrDefault(like.getUser().getUserId(), 0.0);
            scoreByPostId.merge(postId, similarity * LIKE_REACTION_WEIGHT, Double::sum);
        }
        for (Comment comment : commentRepository.findByUserIn(similarUserEntities)) {
            Long postId = comment.getPost().getPostId();
            double similarity = similarityByUserId.getOrDefault(comment.getUser().getUserId(), 0.0);
            scoreByPostId.merge(postId, similarity * COMMENT_REACTION_WEIGHT, Double::sum);
        }

        return scoreByPostId;
    }

    private Map<Long, Double> computeItemBasedScores(User currentUser) {
        Map<Long, Double> myReactionWeightByPostId = new HashMap<>();
        for (PostLike like : postLikeRepository.findByUser(currentUser)) {
            myReactionWeightByPostId.merge(like.getPost().getPostId(), LIKE_REACTION_WEIGHT, Double::sum);
        }
        for (Comment comment : commentRepository.findByUser(currentUser)) {
            myReactionWeightByPostId.merge(comment.getPost().getPostId(), COMMENT_REACTION_WEIGHT, Double::sum);
        }

        if (myReactionWeightByPostId.isEmpty()) {
            return Map.of();
        }

        Set<Long> seedPostIds = myReactionWeightByPostId.keySet();
        List<Post> seedPosts = postRepository.findAllById(seedPostIds);

        Map<Long, Double> coReactorTrust = new HashMap<>();

        for (PostLike like : postLikeRepository.findByPostIn(seedPosts)) {
            Long reactorId = like.getUser().getUserId();
            if (reactorId.equals(currentUser.getUserId())) continue;
            double mySeedWeight = myReactionWeightByPostId.getOrDefault(like.getPost().getPostId(), 0.0);
            coReactorTrust.merge(reactorId, mySeedWeight * LIKE_REACTION_WEIGHT, Double::sum);
        }
        for (Comment comment : commentRepository.findByPostIn(seedPosts)) {
            Long reactorId = comment.getUser().getUserId();
            if (reactorId.equals(currentUser.getUserId())) continue;
            double mySeedWeight = myReactionWeightByPostId.getOrDefault(comment.getPost().getPostId(), 0.0);
            coReactorTrust.merge(reactorId, mySeedWeight * COMMENT_REACTION_WEIGHT, Double::sum);
        }

        if (coReactorTrust.isEmpty()) {
            return Map.of();
        }

        Set<Long> topCoReactorIds = coReactorTrust.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(MAX_CO_REACTORS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        List<User> coReactorUsers = userRepository.findAllById(topCoReactorIds);
        Map<Long, Double> candidateScore = new HashMap<>();

        for (PostLike like : postLikeRepository.findByUserIn(coReactorUsers)) {
            Long postId = like.getPost().getPostId();
            if (seedPostIds.contains(postId)) continue;
            double trust = coReactorTrust.getOrDefault(like.getUser().getUserId(), 0.0);
            candidateScore.merge(postId, trust * LIKE_REACTION_WEIGHT, Double::sum);
        }
        for (Comment comment : commentRepository.findByUserIn(coReactorUsers)) {
            Long postId = comment.getPost().getPostId();
            if (seedPostIds.contains(postId)) continue;
            double trust = coReactorTrust.getOrDefault(comment.getUser().getUserId(), 0.0);
            candidateScore.merge(postId, trust * COMMENT_REACTION_WEIGHT, Double::sum);
        }

        return candidateScore;
    }


    private RecommendationDto toDebugDto(ScoredPost sp) {
        Post post = sp.post();
        ScoreBreakdown b = sp.breakdown();
        return new RecommendationDto(
                post.getPostId(),
                post.getUser().getUsername(),
                post.getLabel(),
                post.getText(),
                post.getCreatedDate(),
                tagNames(post),
                b.likesInWindow(), b.popularityScore(), b.itemCfScore(), b.graphSimScore(),
                b.semanticScore(), b.recencyScore(), b.postAgeHours(), b.followed(),
                b.followedBonus(), b.totalScore()
        );
    }

    private List<String> tagNames(Post post) {
        return post.getTags().stream().map(Tag::getName).collect(Collectors.toList());
    }

    private record ScoredPost(Post post, double score, ScoreBreakdown breakdown) {}

    private record ScoreBreakdown(long likesInWindow, double popularityScore, double itemCfScore,
                                  double graphSimScore, double semanticScore, double recencyScore,
                                  long postAgeHours, boolean followed, double followedBonus,
                                  double totalScore) {}
}