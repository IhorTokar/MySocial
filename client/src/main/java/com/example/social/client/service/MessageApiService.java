package com.example.social.client.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MessageApiService {

    private final ApiClient apiClient;

    public MessageApiService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public ConversationsResult getConversations() throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get("/api/messages/conversations");
        if (!response.isSuccess()) {
            return ConversationsResult.fail("Не вдалося завантажити діалоги (код " + response.statusCode() + ")");
        }

        List<ConversationItem> items = new ArrayList<>();
        for (JsonNode node : apiClient.getObjectMapper().readTree(response.body())) {
            items.add(new ConversationItem(
                    node.get("userId").asLong(),
                    node.get("username").asText(),
                    nullableText(node, "displayName"),
                    nullableText(node, "lastMessage"),
                    nullableText(node, "lastMessageAt"),
                    node.get("unreadCount").asLong()));
        }
        return ConversationsResult.ok(items);
    }

    public MessagesResult getMessagesWith(Long userId) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get("/api/messages/with/" + userId);
        if (!response.isSuccess()) {
            return MessagesResult.fail("Не вдалося завантажити повідомлення (код " + response.statusCode() + ")");
        }

        List<MessageItem> items = new ArrayList<>();
        for (JsonNode node : apiClient.getObjectMapper().readTree(response.body())) {
            items.add(parseMessage(node));
        }
        return MessagesResult.ok(items);
    }

    public boolean markRead(Long userId) throws IOException, InterruptedException {
        return apiClient.post("/api/messages/with/" + userId + "/read", null).isSuccess();
    }

    public UploadResult uploadPhoto(Path file) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.postMultipart("/api/messages/upload", "file", file);
        if (!response.isSuccess()) {
            JsonNode errorNode = apiClient.getObjectMapper().readTree(response.body());
            String error = errorNode.has("error") ? errorNode.get("error").asText() : "Не вдалося завантажити фото";
            return UploadResult.fail(error);
        }
        JsonNode node = apiClient.getObjectMapper().readTree(response.body());
        return UploadResult.ok(node.get("url").asText());
    }

    public static MessageItem parseMessage(JsonNode node) {
        return new MessageItem(
                node.get("messageId").asLong(),
                node.get("senderId").asLong(),
                node.get("receiverId").asLong(),
                nullableText(node, "text"),
                nullableText(node, "photoUrl"),
                node.get("createdAt").asText(),
                node.path("read").asBoolean(),
                node.path("edited").asBoolean(),
                node.path("deleted").asBoolean(),
                node.hasNonNull("parentMessageId") ? node.get("parentMessageId").asLong() : null,
                nullableText(node, "parentSnippet"),
                nullableText(node, "parentSenderUsername")
        );
    }

    private static String nullableText(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    public record MessageItem(Long messageId, Long senderId, Long receiverId, String text, String photoUrl,
                              String createdAt, boolean read, boolean edited, boolean deleted,
                              Long parentMessageId, String parentSnippet, String parentSenderUsername) {
    }

    public record ConversationItem(Long userId, String username, String displayName,
                                   String lastMessage, String lastMessageAt, long unreadCount) {
        public String displayNameOrUsername() {
            return displayName != null && !displayName.isBlank() ? displayName : username;
        }
    }

    public record ConversationsResult(boolean success, List<ConversationItem> conversations, String errorMessage) {
        public static ConversationsResult ok(List<ConversationItem> c) { return new ConversationsResult(true, c, null); }
        public static ConversationsResult fail(String e) { return new ConversationsResult(false, null, e); }
    }

    public record MessagesResult(boolean success, List<MessageItem> messages, String errorMessage) {
        public static MessagesResult ok(List<MessageItem> m) { return new MessagesResult(true, m, null); }
        public static MessagesResult fail(String e) { return new MessagesResult(false, null, e); }
    }

    public record UploadResult(boolean success, String url, String errorMessage) {
        public static UploadResult ok(String url) { return new UploadResult(true, url, null); }
        public static UploadResult fail(String errorMessage) { return new UploadResult(false, null, errorMessage); }
    }
}