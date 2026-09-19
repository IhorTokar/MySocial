package com.example.social.client.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class SavePostApiService {

    private final ApiClient apiClient;

    public SavePostApiService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public boolean getStatus(Long postId) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get("/api/posts/" + postId + "/save/status");
        if (!response.isSuccess()) {
            return false;
        }
        JsonNode node = apiClient.getObjectMapper().readTree(response.body());
        return node.get("saved").asBoolean();
    }

    public ActionResult save(Long postId) throws IOException, InterruptedException {
        return performAction(apiClient.post("/api/posts/" + postId + "/save", null));
    }

    public ActionResult unsave(Long postId) throws IOException, InterruptedException {
        return performAction(apiClient.delete("/api/posts/" + postId + "/save"));
    }

    private ActionResult performAction(ApiClient.ApiResponse response) throws IOException {
        if (response.isSuccess()) {
            return ActionResult.ok();
        }
        JsonNode errorNode = apiClient.getObjectMapper().readTree(response.body());
        String error = errorNode.has("error") ? errorNode.get("error").asText() : "Помилка";
        return ActionResult.fail(error);
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