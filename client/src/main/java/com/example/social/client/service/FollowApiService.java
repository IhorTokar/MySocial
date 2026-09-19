package com.example.social.client.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

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

    private ActionResult toResult(ApiClient.ApiResponse response, String successMessage)
            throws IOException {
        if (response.isSuccess()) {
            return ActionResult.ok(successMessage);
        }
        JsonNode errorNode = apiClient.getObjectMapper().readTree(response.body());
        String error = errorNode.has("error") ? errorNode.get("error").asText() : "Дія не виконана";
        return ActionResult.fail(error);
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