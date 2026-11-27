package com.example.social.client.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Path;
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

    /** Сторінка стрічки: limit постів, починаючи з позиції offset. */
    public FeedResult getFeed(int offset, int limit) throws IOException, InterruptedException {
        return fetchList("/api/posts/feed?offset=" + offset + "&limit=" + limit);
    }

    /** Вкладка «Відстежувані»: пости підписок від найновіших. */
    public FeedResult getFollowingFeed(int offset, int limit) throws IOException, InterruptedException {
        return fetchList("/api/posts/feed/following?offset=" + offset + "&limit=" + limit);
    }

    /** Один пост за id (напр. щойно опублікований — щоб показати його вгорі стрічки); null — не знайдено. */
    public PostItem getPost(Long postId) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get("/api/posts/" + postId);
        if (!response.isSuccess()) {
            return null;
        }
        return parsePost(apiClient.getObjectMapper().readTree(response.body()));
    }

    public FeedResult getPostsByUser(Long userId) throws IOException, InterruptedException {
        return fetchList("/api/posts/user/" + userId);
    }

    /** «Схожі пости»: найближчі за змістом до цього поста (у кожного — matchScore). */
    public FeedResult getSimilarPosts(Long postId, int limit) throws IOException, InterruptedException {
        return fetchList("/api/posts/" + postId + "/similar?limit=" + limit);
    }

    /** Підказаний тег і частка «голосів» схожих постів за нього (0..1). */
    public record TagSuggestion(String tag, double share) {
    }

    /**
     * Підказки тегів за текстом чернетки; порожній список — текст короткий, схожих постів немає
     * або NLP-сервіс недоступний.
     */
    public List<TagSuggestion> suggestTags(String text, List<String> currentTags)
            throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.post("/api/posts/tag-suggestions",
                Map.of("text", text, "tags", currentTags));
        List<TagSuggestion> result = new ArrayList<>();
        if (!response.isSuccess()) {
            return result;
        }
        for (JsonNode node : apiClient.getObjectMapper().readTree(response.body())) {
            result.add(new TagSuggestion(node.get("tag").asText(), node.path("share").asDouble()));
        }
        return result;
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

        JsonNode node = apiClient.getObjectMapper().readTree(response.body());
        return CreatePostResult.ok(node.get("postId").asLong());
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
        return CreatePostResult.ok(postId);
    }

    public CreatePostResult uploadMedia(Long postId, Path file) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.postMultipart("/api/posts/" + postId + "/media", "file", file);

        if (!response.isSuccess()) {
            JsonNode errorNode = apiClient.getObjectMapper().readTree(response.body());
            String error = errorNode.has("error") ? errorNode.get("error").asText() : "Не вдалося завантажити фото";
            return CreatePostResult.fail(error);
        }
        return CreatePostResult.ok(postId);
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
                node.path("savedByCurrentUser").asBoolean(),
                nullableText(node, "recommendationReason"),
                nullableText(node, "recommendationDetails"),
                node.hasNonNull("matchScore") ? node.get("matchScore").asDouble() : null
        );
    }

    private static String nullableText(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    /**
     * @param reason        стрічка «Для вас»: чому пост показано (null поза рекомендаціями)
     * @param reasonDetails внесок кожної ознаки в оцінку — для підказки при наведенні
     * @param matchScore    пошук за змістом і «схожі пости»: схожість 0..1 (null — не застосовно)
     */
    public record PostItem(Long postId, Long authorId, String authorUsername, String authorDisplayName,
                           String authorAvatarUrl, String label, String text, String mediaUrl,
                           String createdDate, List<String> tags, long likesCount, long commentsCount,
                           boolean likedByCurrentUser, boolean savedByCurrentUser,
                           String reason, String reasonDetails, Double matchScore) {

        public String authorNameOrUsername() {
            return authorDisplayName != null && !authorDisplayName.isBlank() ? authorDisplayName : authorUsername;
        }

        public PostItem withLike(boolean liked, long count) {
            return new PostItem(postId, authorId, authorUsername, authorDisplayName, authorAvatarUrl,
                    label, text, mediaUrl, createdDate, tags, count, commentsCount, liked, savedByCurrentUser,
                    reason, reasonDetails, matchScore);
        }

        public PostItem withSaved(boolean saved) {
            return new PostItem(postId, authorId, authorUsername, authorDisplayName, authorAvatarUrl,
                    label, text, mediaUrl, createdDate, tags, likesCount, commentsCount,
                    likedByCurrentUser, saved, reason, reasonDetails, matchScore);
        }

        public PostItem withCommentsCount(long count) {
            return new PostItem(postId, authorId, authorUsername, authorDisplayName, authorAvatarUrl,
                    label, text, mediaUrl, createdDate, tags, likesCount, count,
                    likedByCurrentUser, savedByCurrentUser, reason, reasonDetails, matchScore);
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

    public record CreatePostResult(boolean success, Long postId, String errorMessage) {
        public static CreatePostResult ok(Long postId) {
            return new CreatePostResult(true, postId, null);
        }

        public static CreatePostResult fail(String errorMessage) {
            return new CreatePostResult(false, null, errorMessage);
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