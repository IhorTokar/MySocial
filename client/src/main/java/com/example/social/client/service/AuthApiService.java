package com.example.social.client.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Map;

public class AuthApiService {

    private final ApiClient apiClient;

    public AuthApiService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public LoginResult login(String username, String password) throws IOException, InterruptedException {
        Map<String, String> body = Map.of("username", username, "password", password);
        ApiClient.ApiResponse response = apiClient.postPublic("/api/auth/login", body);

        if (!response.isSuccess()) {
            JsonNode errorNode = apiClient.getObjectMapper().readTree(response.body());
            String error = errorNode.has("error") ? errorNode.get("error").asText() : "Login failed";
            return LoginResult.fail(error);
        }

        JsonNode node = apiClient.getObjectMapper().readTree(response.body());
        return LoginResult.ok(
                node.get("token").asText(),
                node.get("userId").asLong(),
                node.get("username").asText(),
                node.path("role").asText("user")
        );
    }

    public RegisterResult register(String username, String email, String password)
            throws IOException, InterruptedException {
        Map<String, String> body = Map.of("username", username, "email", email, "password", password);
        ApiClient.ApiResponse response = apiClient.postPublic("/api/auth/register", body);

        if (!response.isSuccess()) {
            JsonNode errorNode = apiClient.getObjectMapper().readTree(response.body());
            String error = errorNode.has("error") ? errorNode.get("error").asText() : "Registration failed";
            return RegisterResult.fail(error);
        }

        return RegisterResult.ok();
    }

    /**
     * Заявка «Забув пароль». Сервер навмисно відповідає однаково, чи існує такий акаунт, чи ні.
     * @return null — заявку прийнято, інакше текст помилки
     */
    public String requestPasswordReset(String login, String contact, String message)
            throws IOException, InterruptedException {
        Map<String, String> body = Map.of(
                "login", login,
                "contact", contact == null ? "" : contact,
                "message", message == null ? "" : message);
        ApiClient.ApiResponse response = apiClient.postPublic("/api/auth/password-reset-requests", body);
        if (response.isSuccess()) {
            return null;
        }
        JsonNode node = apiClient.getObjectMapper().readTree(response.body());
        return node.has("error") ? node.get("error").asText() : "Не вдалося надіслати заявку (" + response.statusCode() + ")";
    }

    public record LoginResult(boolean success, String token, Long userId, String username, String role,
                              String errorMessage) {
        public static LoginResult ok(String token, Long userId, String username, String role) {
            return new LoginResult(true, token, userId, username, role, null);
        }

        public static LoginResult fail(String errorMessage) {
            return new LoginResult(false, null, null, null, null, errorMessage);
        }
    }

    public record RegisterResult(boolean success, String errorMessage) {
        public static RegisterResult ok() {
            return new RegisterResult(true, null);
        }

        public static RegisterResult fail(String errorMessage) {
            return new RegisterResult(false, errorMessage);
        }
    }
}