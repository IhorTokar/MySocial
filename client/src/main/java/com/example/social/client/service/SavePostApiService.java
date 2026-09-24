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

    public FetchResult getSavedPosts() throws java.io.IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get("/api/posts/saved");

        if (!response.isSuccess()) {
            return FetchResult.fail("Не вдалося завантажити збережені пости");
        }

        java.util.List<PostApiService.PostItem> posts = new java.util.ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode arrayNode = apiClient.getObjectMapper().readTree(response.body());

        for (com.fasterxml.jackson.databind.JsonNode node : arrayNode) {
            posts.add(new PostApiService.PostItem(
                    node.get("postId").asLong(),
                    node.get("authorUsername").asText(),
                    node.has("label") && !node.get("label").isNull() ? node.get("label").asText() : null,
                    node.get("text").asText(),
                    node.has("mediaUrl") && !node.get("mediaUrl").isNull() ? node.get("mediaUrl").asText() : null,
                    node.get("createdDate").asText()
            ));
        }

        return FetchResult.ok(posts);
    }

    public record FetchResult(boolean success, java.util.List<PostApiService.PostItem> posts, String errorMessage) {
        public static FetchResult ok(java.util.List<PostApiService.PostItem> posts) {
            return new FetchResult(true, posts, null);
        }

        public static FetchResult fail(String errorMessage) {
            return new FetchResult(false, null, errorMessage);
        }
    }
}