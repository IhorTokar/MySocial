package com.example.social.client.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.example.social.shared.dto.UserEchoPointDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GraphApiService {

    private final ApiClient apiClient;

    public GraphApiService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public CommunityResult getUserCommunity(Long userId) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get("/api/graph/community/" + userId);
        if (!response.isSuccess()) {
            return CommunityResult.notFound();
        }

        JsonNode node = apiClient.getObjectMapper().readTree(response.body());
        List<String> topTags = new ArrayList<>();
        for (JsonNode tag : node.get("topTags")) {
            topTags.add(tag.get("tag").asText());
        }

        return CommunityResult.ok(new CommunityInfo(
                node.get("community").asLong(),
                node.get("memberCount").asInt(),
                topTags,
                node.get("echoChamberScore").asDouble()
        ));
    }

    /** Траєкторія користувача по знімках історії ехо-камер (Louvain), від найстарішого; при помилці — порожньо. */
    public List<UserEchoPointDto> getUserCommunityHistory(Long userId) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get("/api/graph/community/" + userId + "/history");
        if (!response.isSuccess()) {
            return List.of();
        }
        return apiClient.getObjectMapper().readValue(response.body(), new TypeReference<List<UserEchoPointDto>>() {});
    }

    public record CommunityInfo(long communityId, int memberCount, List<String> topTags, double echoChamberScore) {
    }

    public record CommunityResult(boolean found, CommunityInfo info) {
        public static CommunityResult ok(CommunityInfo info) {
            return new CommunityResult(true, info);
        }
        public static CommunityResult notFound() {
            return new CommunityResult(false, null);
        }
    }
}