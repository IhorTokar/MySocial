package com.example.social.server.service;

import com.example.social.server.entity.Followers;
import com.example.social.server.entity.User;
import com.example.social.server.repository.FollowersRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserSimilarityService {

    private final GraphEmbeddingService graphEmbeddingService;
    private final FollowersRepository followersRepository;
    private final com.example.social.server.repository.UserRepository userRepository;

    public UserSimilarityService(GraphEmbeddingService graphEmbeddingService,
                                 FollowersRepository followersRepository,
                                 com.example.social.server.repository.UserRepository userRepository) {
        this.graphEmbeddingService = graphEmbeddingService;
        this.followersRepository = followersRepository;
        this.userRepository = userRepository;
    }

    public List<Map<String, Object>> getSimilarUsers(Long userId, int topN, boolean excludeFollowed) {
        List<Map<String, Object>> allEmbeddings = graphEmbeddingService.getEmbeddings();

        Map<String, Object> targetEntry = allEmbeddings.stream()
                .filter(e -> userId.equals(e.get("userId")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No embedding found for user " + userId + " — run /api/graph/embeddings first"));

        @SuppressWarnings("unchecked")
        List<Double> targetVector = (List<Double>) targetEntry.get("embedding");

        Set<Long> excludedIds = new HashSet<>();
        excludedIds.add(userId);

        if (excludeFollowed) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            for (Followers rel : followersRepository.findByFollower(user)) {
                excludedIds.add(rel.getFollowing().getUserId());
            }
        }

        List<Map<String, Object>> scored = new ArrayList<>();

        for (Map<String, Object> entry : allEmbeddings) {
            Long candidateId = (Long) entry.get("userId");
            if (excludedIds.contains(candidateId)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            List<Double> candidateVector = (List<Double>) entry.get("embedding");

            double similarity = cosineSimilarity(targetVector, candidateVector);

            Map<String, Object> result = new HashMap<>();
            result.put("userId", candidateId);
            result.put("username", entry.get("username"));
            result.put("similarityScore", similarity);
            scored.add(result);
        }

        return scored.stream()
                .sorted((a, b) -> Double.compare(
                        (Double) b.get("similarityScore"),
                        (Double) a.get("similarityScore")))
                .limit(topN)
                .collect(Collectors.toList());
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a.size() != b.size()) {
            throw new IllegalArgumentException("Vector dimensions do not match");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.size(); i++) {
            dotProduct += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}