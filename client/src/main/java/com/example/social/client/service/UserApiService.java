package com.example.social.client.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Map;

public class UserApiService {

    private final ApiClient apiClient;

    public UserApiService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public UserProfileResult getUser(Long userId) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get("/api/users/" + userId);
        if (!response.isSuccess()) {
            return UserProfileResult.fail("Не вдалося завантажити профіль (код " + response.statusCode() + ")");
        }
        return UserProfileResult.ok(parseProfile(response.body()));
    }

    public UserProfileResult getMyProfile() throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get("/api/users/me");
        if (!response.isSuccess()) {
            return UserProfileResult.fail("Не вдалося завантажити профіль (код " + response.statusCode() + ")");
        }
        return UserProfileResult.ok(parseProfile(response.body()));
    }

    private UserProfile parseProfile(String body) throws IOException {
        JsonNode node = apiClient.getObjectMapper().readTree(body);
        return new UserProfile(
                node.get("userId").asLong(),
                node.get("username").asText(),
                node.has("displayName") && !node.get("displayName").isNull() ? node.get("displayName").asText() : null,
                node.has("aboutMe") && !node.get("aboutMe").isNull() ? node.get("aboutMe").asText() : null,
                node.has("userAvatarUrl") && !node.get("userAvatarUrl").isNull() ? node.get("userAvatarUrl").asText() : null,
                node.get("postsCount").asLong(),
                node.get("followersCount").asLong(),
                node.get("followingCount").asLong(),
                node.get("followedByCurrentUser").asBoolean()
        );
    }

    public record UserProfile(Long userId, String username, String displayName, String aboutMe,
                              String userAvatarUrl, long postsCount, long followersCount,
                              long followingCount, boolean followedByCurrentUser) {

        public String displayNameOrUsername() {
            return displayName != null && !displayName.isBlank() ? displayName : username;
        }
    }

    public record UserProfileResult(boolean success, UserProfile profile, String errorMessage) {
        public static UserProfileResult ok(UserProfile profile) {
            return new UserProfileResult(true, profile, null);
        }
        public static UserProfileResult fail(String errorMessage) {
            return new UserProfileResult(false, null, errorMessage);
        }
    }

    public UserProfileResult updateProfile(String displayName, String aboutMe)
            throws IOException, InterruptedException {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        if (displayName != null) body.put("displayName", displayName);
        if (aboutMe != null) body.put("aboutMe", aboutMe);

        ApiClient.ApiResponse response = apiClient.patch("/api/users/me", body);
        if (!response.isSuccess()) {
            return UserProfileResult.fail("Не вдалося оновити профіль (код " + response.statusCode() + ")");
        }
        return UserProfileResult.ok(parseProfile(response.body()));
    }

    public UserProfileResult uploadAvatar(java.nio.file.Path file) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.postMultipart("/api/users/me/avatar", "file", file);
        if (!response.isSuccess()) {
            return UserProfileResult.fail(extractError(response, "Не вдалося завантажити фото"));
        }
        return UserProfileResult.ok(parseProfile(response.body()));
    }

    private String extractError(ApiClient.ApiResponse response, String fallback) {
        try {
            JsonNode node = apiClient.getObjectMapper().readTree(response.body());
            if (node.has("error")) {
                return node.get("error").asText();
            }
        } catch (Exception ignored) {
            // тіло не JSON
        }
        return fallback + " (код " + response.statusCode() + ")";
    }

    public ActionResult changePassword(String currentPassword, String newPassword)
            throws IOException, InterruptedException {

        Map<String, String> body = Map.of("currentPassword", currentPassword, "newPassword", newPassword);
        ApiClient.ApiResponse response = apiClient.patch("/api/users/me/password", body);

        if (!response.isSuccess()) {
            return ActionResult.fail(extractError(response, "Не вдалося змінити пароль"));
        }
        return ActionResult.ok();
    }

    public ActionResult deleteAccount(String password) throws IOException, InterruptedException {
        Map<String, String> body = Map.of("password", password);
        ApiClient.ApiResponse response = apiClient.deleteWithBody("/api/users/me", body);

        if (!response.isSuccess()) {
            return ActionResult.fail(extractError(response, "Не вдалося видалити акаунт"));
        }
        return ActionResult.ok();
    }

    public record ActionResult(boolean success, String errorMessage) {
        public static ActionResult ok() { return new ActionResult(true, null); }
        public static ActionResult fail(String errorMessage) { return new ActionResult(false, errorMessage); }
    }
}