package com.example.social.client.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PostApiService {

    private final ApiClient apiClient;

    public PostApiService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public FeedResult getFeed() throws IOException, InterruptedException {
        return fetchList("/api/posts/feed");
    }

    public FeedResult getPostsByUser(Long userId) throws IOException, InterruptedException {
        return fetchList("/api/posts/user/" + userId);
    }

    public CreatePostResult createPost(String label, String text, List<String> tags)
            throws IOException, InterruptedException {

        Map<String, Object> body = Map.of(
                "label", label == null ? "" : label,
                "text", text,
                "tags", tags
        );

        ApiClient.ApiResponse response = apiClient.post("/api/posts", body);

        if (!response.isSuccess()) {
            JsonNode errorNode = apiClient.getObjectMapper().readTree(response.body());
            String error = errorNode.has("error") ? errorNode.get("error").asText() : "Не вдалося створити пост";
            return CreatePostResult.fail(error);
        }

        return CreatePostResult.ok();
    }

    private FeedResult fetchList(String path) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get(path);

        if (!response.isSuccess()) {
            return FeedResult.fail("Не вдалося завантажити пости (код " + response.statusCode() + ")");
        }

        List<PostItem> posts = new ArrayList<>();
        JsonNode arrayNode = apiClient.getObjectMapper().readTree(response.body());

        for (JsonNode node : arrayNode) {
            posts.add(new PostItem(
                    node.get("postId").asLong(),
                    node.get("authorUsername").asText(),
                    node.has("label") && !node.get("label").isNull() ? node.get("label").asText() : null,
                    node.get("text").asText(),
                    node.has("mediaUrl") && !node.get("mediaUrl").isNull() ? node.get("mediaUrl").asText() : null,
                    node.get("createdDate").asText()
            ));
        }

        return FeedResult.ok(posts);
    }

    public record PostItem(Long postId, String authorUsername, String label,
                           String text, String mediaUrl, String createdDate) {
    }

    public record FeedResult(boolean success, List<PostItem> posts, String errorMessage) {
        public static FeedResult ok(List<PostItem> posts) {
            return new FeedResult(true, posts, null);
        }

        public static FeedResult fail(String errorMessage) {
            return new FeedResult(false, null, errorMessage);
        }
    }

    public record CreatePostResult(boolean success, String errorMessage) {
        public static CreatePostResult ok() {
            return new CreatePostResult(true, null);
        }

        public static CreatePostResult fail(String errorMessage) {
            return new CreatePostResult(false, errorMessage);
        }
    }
}