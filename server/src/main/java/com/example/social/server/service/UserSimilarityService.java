package com.example.social.server.service;

import com.example.social.server.exception.*;

import com.example.social.server.entity.Followers;
import com.example.social.server.entity.User;
import com.example.social.server.repository.FollowersRepository;
import com.example.social.shared.dto.SimilarUserDto;
import com.example.social.shared.dto.UserEmbeddingDto;
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

    public List<SimilarUserDto> getSimilarUsers(Long userId, int topN, boolean excludeFollowed) {
        List<UserEmbeddingDto> allEmbeddings = graphEmbeddingService.getEmbeddings();

        UserEmbeddingDto targetEntry = allEmbeddings.stream()
                .filter(e -> e.userId() == userId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "No embedding found for user " + userId + " — run /api/graph/embeddings first"));

        List<Double> targetVector = targetEntry.embedding();

        Set<Long> excludedIds = new HashSet<>();
        excludedIds.add(userId);

        if (excludeFollowed) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User not found: " + userId));
            for (Followers rel : followersRepository.findByFollower(user)) {
                excludedIds.add(rel.getFollowing().getUserId());
            }
        }

        List<SimilarUserDto> scored = new ArrayList<>();

        for (UserEmbeddingDto entry : allEmbeddings) {
            long candidateId = entry.userId();
            if (excludedIds.contains(candidateId)) {
                continue;
            }
            double similarity = cosineSimilarity(targetVector, entry.embedding());
            scored.add(new SimilarUserDto(candidateId, entry.username(), similarity));
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(SimilarUserDto::similarityScore).reversed())
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