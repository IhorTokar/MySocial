package com.example.social.client.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SearchApiService {

    private final ApiClient apiClient;

    public SearchApiService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public SearchResult search(String query) throws IOException, InterruptedException {
        return search(query, false);
    }

    /** @param semantic пости шукати за змістом (вектори тексту), а не за збігом слів */
    public SearchResult search(String query, boolean semantic) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        ApiClient.ApiResponse response = apiClient.get("/api/search?q=" + encoded
                + (semantic ? "&mode=semantic" : ""));

        if (!response.isSuccess()) {
            return SearchResult.fail("Не вдалося виконати пошук (код " + response.statusCode() + ")");
        }

        JsonNode root = apiClient.getObjectMapper().readTree(response.body());

        List<UserSummary> users = new ArrayList<>();
        for (JsonNode node : root.get("users")) {
            users.add(new UserSummary(
                    node.get("userId").asLong(),
                    node.get("username").asText(),
                    node.hasNonNull("displayName") ? node.get("displayName").asText() : null,
                    node.hasNonNull("avatarUrl") ? node.get("avatarUrl").asText() : null,
                    node.path("followedByCurrentUser").asBoolean()
            ));
        }

        List<PostApiService.PostItem> posts = new ArrayList<>();
        for (JsonNode node : root.get("posts")) {
            posts.add(PostApiService.parsePost(node));
        }

        return SearchResult.ok(users, posts, root.path("mode").asText("text"));
    }

    public record UserSummary(Long userId, String username, String displayName,
                              String avatarUrl, boolean followedByCurrentUser) {
        public String nameOrUsername() {
            return displayName != null && !displayName.isBlank() ? displayName : username;
        }
    }

    /**
     * @param mode "text", "semantic" або "semantic-unavailable" (просили за змістом, але NLP-сервіс
     *             недоступний — сервер показав звичайний пошук)
     */
    public record SearchResult(boolean success, List<UserSummary> users,
                               List<PostApiService.PostItem> posts, String errorMessage, String mode) {
        public static SearchResult ok(List<UserSummary> users, List<PostApiService.PostItem> posts, String mode) {
            return new SearchResult(true, users, posts, null, mode);
        }
        public static SearchResult fail(String errorMessage) {
            return new SearchResult(false, List.of(), List.of(), errorMessage, "text");
        }
    }
}