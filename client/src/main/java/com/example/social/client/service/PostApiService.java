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
        for (JsonNode node : apiClient.getObjectMapper().readTree(response.body())) {
            posts.add(parsePost(node));
        }
        return FeedResult.ok(posts);
    }

    /** Єдине місце розбору поста з JSON: використовують і стрічка, і «Збережені». */
    public static PostItem parsePost(JsonNode node) {
        List<String> tags = new ArrayList<>();
        if (node.has("tags") && node.get("tags").isArray()) {
            for (JsonNode tag : node.get("tags")) {
                tags.add(tag.asText());
            }
        }
        return new PostItem(
                node.get("postId").asLong(),
                node.path("authorId").asLong(),
                node.get("authorUsername").asText(),
                nullableText(node, "authorDisplayName"),
                nullableText(node, "authorAvatarUrl"),
                nullableText(node, "label"),
                node.get("text").asText(),
                nullableText(node, "mediaUrl"),
                node.get("createdDate").asText(),
                tags,
                node.path("likesCount").asLong(),
                node.path("commentsCount").asLong(),
                node.path("likedByCurrentUser").asBoolean(),
                node.path("savedByCurrentUser").asBoolean()
        );
    }

    private static String nullableText(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    public record PostItem(Long postId, Long authorId, String authorUsername, String authorDisplayName,
                           String authorAvatarUrl, String label, String text, String mediaUrl,
                           String createdDate, List<String> tags, long likesCount, long commentsCount,
                           boolean likedByCurrentUser, boolean savedByCurrentUser) {

        public String authorNameOrUsername() {
            return authorDisplayName != null && !authorDisplayName.isBlank() ? authorDisplayName : authorUsername;
        }

        public PostItem withLike(boolean liked, long count) {
            return new PostItem(postId, authorId, authorUsername, authorDisplayName, authorAvatarUrl,
                    label, text, mediaUrl, createdDate, tags, count, commentsCount, liked, savedByCurrentUser);
        }

        public PostItem withSaved(boolean saved) {
            return new PostItem(postId, authorId, authorUsername, authorDisplayName, authorAvatarUrl,
                    label, text, mediaUrl, createdDate, tags, likesCount, commentsCount,
                    likedByCurrentUser, saved);
        }

        public PostItem withCommentsCount(long count) {
            return new PostItem(postId, authorId, authorUsername, authorDisplayName, authorAvatarUrl,
                    label, text, mediaUrl, createdDate, tags, likesCount, count,
                    likedByCurrentUser, savedByCurrentUser);
        }
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

    public CreatePostResult updatePost(Long postId, String label, String text, List<String> tags)
            throws IOException, InterruptedException {

        Map<String, Object> body = Map.of(
                "label", label == null ? "" : label,
                "text", text,
                "tags", tags
        );

        ApiClient.ApiResponse response = apiClient.patch("/api/posts/" + postId, body);

        if (!response.isSuccess()) {
            JsonNode errorNode = apiClient.getObjectMapper().readTree(response.body());
            String error = errorNode.has("error") ? errorNode.get("error").asText() : "Не вдалося оновити пост";
            return CreatePostResult.fail(error);
        }
        return CreatePostResult.ok();
    }

    public ActionResult deletePost(Long postId) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.delete("/api/posts/" + postId);
        if (response.isSuccess()) {
            return ActionResult.ok();
        }
        JsonNode errorNode = apiClient.getObjectMapper().readTree(response.body());
        String error = errorNode.has("error") ? errorNode.get("error").asText() : "Не вдалося видалити пост";
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