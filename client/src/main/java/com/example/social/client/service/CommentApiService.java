package com.example.social.client.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CommentApiService {

    private final ApiClient apiClient;

    public CommentApiService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public CommentsResult getComments(Long postId) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get("/api/posts/" + postId + "/comments");

        if (!response.isSuccess()) {
            return CommentsResult.fail("Не вдалося завантажити коментарі");
        }

        List<CommentItem> comments = new ArrayList<>();
        JsonNode arrayNode = apiClient.getObjectMapper().readTree(response.body());

        for (JsonNode node : arrayNode) {
            comments.add(new CommentItem(
                    node.get("commentId").asLong(),
                    node.get("authorUsername").asText(),
                    node.get("text").asText()
            ));
        }

        return CommentsResult.ok(comments);
    }

    public ActionResult addComment(Long postId, String text) throws IOException, InterruptedException {
        Map<String, String> body = Map.of("text", text);
        ApiClient.ApiResponse response = apiClient.post("/api/posts/" + postId + "/comments", body);

        if (response.isSuccess()) {
            return ActionResult.ok();
        }

        JsonNode errorNode = apiClient.getObjectMapper().readTree(response.body());
        String error = errorNode.has("error") ? errorNode.get("error").asText() : "Не вдалося додати коментар";
        return ActionResult.fail(error);
    }

    public record CommentItem(Long commentId, String authorUsername, String text) {
    }

    public record CommentsResult(boolean success, List<CommentItem> comments, String errorMessage) {
        public static CommentsResult ok(List<CommentItem> comments) {
            return new CommentsResult(true, comments, null);
        }

        public static CommentsResult fail(String errorMessage) {
            return new CommentsResult(false, null, errorMessage);
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