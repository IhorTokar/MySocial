package com.example.social.client.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FollowApiService {

    private final ApiClient apiClient;

    public FollowApiService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ActionResult follow(Long userId) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.post("/api/followers/" + userId, null);
        return toResult(response, "Підписано");
    }

    public ActionResult unfollow(Long userId) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.delete("/api/followers/" + userId);
        return toResult(response, "Відписано");
    }

    public long getFollowersCount(Long userId) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get("/api/followers/" + userId + "/count");
        if (!response.isSuccess()) {
            return 0;
        }
        JsonNode node = apiClient.getObjectMapper().readTree(response.body());
        return node.get("followersCount").asLong();
    }

    public ListResult getFollowers(Long userId) throws IOException, InterruptedException {
        return fetchList("/api/followers/" + userId + "/summary");
    }

    public ListResult getFollowing(Long userId) throws IOException, InterruptedException {
        return fetchList("/api/followers/" + userId + "/following/summary");
    }

    private ListResult fetchList(String path) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get(path);
        if (!response.isSuccess()) {
            return ListResult.fail("Не вдалося завантажити список (код " + response.statusCode() + ")");
        }

        List<UserSummary> items = new ArrayList<>();
        for (JsonNode node : apiClient.getObjectMapper().readTree(response.body())) {
            items.add(new UserSummary(
                    node.get("userId").asLong(),
                    node.get("username").asText(),
                    node.hasNonNull("displayName") ? node.get("displayName").asText() : null,
                    node.hasNonNull("avatarUrl") ? node.get("avatarUrl").asText() : null,
                    node.path("followedByCurrentUser").asBoolean()
            ));
        }
        return ListResult.ok(items);
    }

    private ActionResult toResult(ApiClient.ApiResponse response, String successMessage)
            throws IOException {
        if (response.isSuccess()) {
            return ActionResult.ok(successMessage);
        }
        JsonNode errorNode = apiClient.getObjectMapper().readTree(response.body());
        String error = errorNode.has("error") ? errorNode.get("error").asText() : "Дія не виконана";
        return ActionResult.fail(error);
    }

    public record UserSummary(Long userId, String username, String displayName,
                              String avatarUrl, boolean followedByCurrentUser) {
        public String nameOrUsername() {
            return displayName != null && !displayName.isBlank() ? displayName : username;
        }
    }

    public record ListResult(boolean success, List<UserSummary> users, String errorMessage) {
        public static ListResult ok(List<UserSummary> users) {
            return new ListResult(true, users, null);
        }
        public static ListResult fail(String errorMessage) {
            return new ListResult(false, List.of(), errorMessage);
        }
    }

    public record ActionResult(boolean success, String message) {
        public static ActionResult ok(String message) {
            return new ActionResult(true, message);
        }
        public static ActionResult fail(String message) {
            return new ActionResult(false, message);
        }
    }
}