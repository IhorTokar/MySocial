package com.example.social.client.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class LikeApiService {

    private final ApiClient apiClient;

    public LikeApiService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public StatusResult getStatus(Long postId) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get("/api/posts/" + postId + "/like/status");
        if (!response.isSuccess()) {
            return StatusResult.error("Не вдалося отримати статус");
        }
        JsonNode node = apiClient.getObjectMapper().readTree(response.body());
        return StatusResult.of(node.get("liked").asBoolean(), node.get("likesCount").asLong());
    }

    public ActionResult like(Long postId) throws IOException, InterruptedException {
        return performAction(apiClient.post("/api/posts/" + postId + "/like", null));
    }

    public ActionResult unlike(Long postId) throws IOException, InterruptedException {
        return performAction(apiClient.delete("/api/posts/" + postId + "/like"));
    }

    private ActionResult performAction(ApiClient.ApiResponse response) throws IOException {
        if (response.isSuccess()) {
            return ActionResult.ok();
        }
        JsonNode errorNode = apiClient.getObjectMapper().readTree(response.body());
        String error = errorNode.has("error") ? errorNode.get("error").asText() : "Помилка";
        return ActionResult.fail(error);
    }

    public record StatusResult(boolean liked, long likesCount, boolean error) {
        public static StatusResult of(boolean liked, long likesCount) {
            return new StatusResult(liked, likesCount, false);
        }

        public static StatusResult error(String message) {
            return new StatusResult(false, 0, true);
        }
    }

    public record ActionResult(boolean success, String errorMessage) {
        public static ActionResult ok() {
            return new ActionResult(true, null);
        }

        public static ActionResult fail(String errorMessage) {
            return new ActionResult(false, errorMessage);
        }
    }
}