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
        for (JsonNode node : apiClient.getObjectMapper().readTree(response.body())) {
            comments.add(parseComment(node));
        }
        return CommentsResult.ok(comments);
    }

    public ActionResult addComment(Long postId, String text, Long parentCommentId)
            throws IOException, InterruptedException {

        Map<String, Object> body = parentCommentId != null
                ? Map.of("text", text, "parentCommentId", parentCommentId)
                : Map.of("text", text);

        ApiClient.ApiResponse response = apiClient.post("/api/posts/" + postId + "/comments", body);

        if (!response.isSuccess()) {
            JsonNode errorNode = apiClient.getObjectMapper().readTree(response.body());
            String error = errorNode.has("error") ? errorNode.get("error").asText() : "Не вдалося додати коментар";
            return ActionResult.fail(error);
        }

        return ActionResult.ok(parseComment(apiClient.getObjectMapper().readTree(response.body())));
    }

    public ActionResult deleteComment(Long commentId) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.delete("/api/comments/" + commentId);
        if (response.isSuccess()) {
            return ActionResult.ok(null);
        }
        JsonNode errorNode = apiClient.getObjectMapper().readTree(response.body());
        String error = errorNode.has("error") ? errorNode.get("error").asText() : "Не вдалося видалити коментар";
        return ActionResult.fail(error);
    }

    private static CommentItem parseComment(JsonNode node) {
        return new CommentItem(
                node.get("commentId").asLong(),
                node.path("authorId").asLong(),
                node.get("authorUsername").asText(),
                nullableText(node, "authorDisplayName"),
                nullableText(node, "authorAvatarUrl"),
                node.get("text").asText(),
                node.get("createdAt").asText(),
                node.hasNonNull("parentCommentId") ? node.get("parentCommentId").asLong() : null
        );
    }

    private static String nullableText(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    public record CommentItem(Long commentId, Long authorId, String authorUsername, String authorDisplayName,
                              String authorAvatarUrl, String text, String createdAt, Long parentCommentId) {
        public String authorNameOrUsername() {
            return authorDisplayName != null && !authorDisplayName.isBlank() ? authorDisplayName : authorUsername;
        }
    }

    public record CommentsResult(boolean success, List<CommentItem> comments, String errorMessage) {
        public static CommentsResult ok(List<CommentItem> comments) {
            return new CommentsResult(true, comments, null);
        }
        public static CommentsResult fail(String errorMessage) {
            return new CommentsResult(false, null, errorMessage);
        }
    }

    public record ActionResult(boolean success, CommentItem comment, String errorMessage) {
        public static ActionResult ok(CommentItem comment) {
            return new ActionResult(true, comment, null);
        }
        public static ActionResult fail(String errorMessage) {
            return new ActionResult(false, null, errorMessage);
        }
    }
}