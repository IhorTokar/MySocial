package com.example.social.client.service;

import com.example.social.shared.dto.AuditLogEntryDto;
import com.example.social.shared.dto.RecommendationWeightsDto;
import com.example.social.shared.dto.WeightsUpdateRequestDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.social.shared.dto.AdminCommentDto;
import com.example.social.shared.dto.AdminPasswordResetDto;
import com.example.social.shared.dto.AdminProfileUpdateDto;
import com.example.social.shared.dto.AdminUserDto;
import com.example.social.shared.dto.BlockUserRequestDto;
import com.example.social.shared.dto.ChangeRoleRequestDto;
import com.example.social.shared.dto.CommunityDetectionResultDto;
import com.example.social.shared.dto.GraphDataDto;
import com.example.social.shared.dto.GraphSyncResultDto;
import java.util.ArrayList;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.io.IOException;
import java.util.List;

/**
 * Запити до /api/admin/**. Відповіді читаються одразу в DTO з модуля shared —
 * ті самі класи, що повертає сервер. При помилці кидається AdminApiException
 * з текстом поля "error" з відповіді сервера.
 */
public class AdminApiService {

    private final ApiClient apiClient;
    private final ObjectMapper mapper;

    public AdminApiService(ApiClient apiClient) {
        this.apiClient = apiClient;
        this.mapper = apiClient.getObjectMapper();
    }

    // ===== Ваги =====

    public RecommendationWeightsDto getWeights() throws IOException, InterruptedException, AdminApiException {
        return read(apiClient.get("/api/admin/weights"), new TypeReference<RecommendationWeightsDto>() {});
    }

    public RecommendationWeightsDto updateWeights(WeightsUpdateRequestDto request)
            throws IOException, InterruptedException, AdminApiException {
        return read(apiClient.put("/api/admin/weights", request), new TypeReference<RecommendationWeightsDto>() {});
    }

    public List<RecommendationWeightsDto> getWeightsHistory(int limit)
            throws IOException, InterruptedException, AdminApiException {
        return read(apiClient.get("/api/admin/weights/history?limit=" + limit),
                new TypeReference<List<RecommendationWeightsDto>>() {});
    }

    public RecommendationWeightsDto restoreWeights(long versionId)
            throws IOException, InterruptedException, AdminApiException {
        return read(apiClient.post("/api/admin/weights/history/" + versionId + "/restore", null),
                new TypeReference<RecommendationWeightsDto>() {});
    }

    // ===== Користувачі =====

    public List<AdminUserDto> searchUsers(String query) throws IOException, InterruptedException, AdminApiException {
        return read(apiClient.get("/api/admin/users?q=" + encode(query)),
                new TypeReference<List<AdminUserDto>>() {});
    }

    public AdminUserDto getUser(long userId) throws IOException, InterruptedException, AdminApiException {
        return read(apiClient.get("/api/admin/users/" + userId), new TypeReference<AdminUserDto>() {});
    }

    public AdminUserDto blockUser(long userId, String reason)
            throws IOException, InterruptedException, AdminApiException {
        return read(apiClient.post("/api/admin/users/" + userId + "/block", new BlockUserRequestDto(reason)),
                new TypeReference<AdminUserDto>() {});
    }

    public AdminUserDto unblockUser(long userId) throws IOException, InterruptedException, AdminApiException {
        return read(apiClient.post("/api/admin/users/" + userId + "/unblock", null),
                new TypeReference<AdminUserDto>() {});
    }

    public AdminUserDto changeRole(long userId, String role)
            throws IOException, InterruptedException, AdminApiException {
        return read(apiClient.put("/api/admin/users/" + userId + "/role", new ChangeRoleRequestDto(role)),
                new TypeReference<AdminUserDto>() {});
    }

    public AdminUserDto updateProfile(long userId, AdminProfileUpdateDto request)
            throws IOException, InterruptedException, AdminApiException {
        return read(apiClient.patch("/api/admin/users/" + userId + "/profile", request),
                new TypeReference<AdminUserDto>() {});
    }

    public void resetPassword(long userId, String newPassword)
            throws IOException, InterruptedException, AdminApiException {
        ensureSuccess(apiClient.post("/api/admin/users/" + userId + "/password",
                new AdminPasswordResetDto(newPassword)));
    }

    // ===== Пости й коментарі =====

    /** Пост читається тим самим парсером, що й у стрічці (PostResponseDto не має порожнього конструктора). */
    public PostApiService.PostItem getPost(long postId) throws IOException, InterruptedException, AdminApiException {
        ApiClient.ApiResponse response = apiClient.get("/api/admin/posts/" + postId);
        ensureSuccess(response);
        return PostApiService.parsePost(mapper.readTree(response.body()));
    }

    public void deletePost(long postId, String reason) throws IOException, InterruptedException, AdminApiException {
        ensureSuccess(apiClient.delete("/api/admin/posts/" + postId + reasonQuery(reason)));
    }

    public AdminCommentDto getComment(long commentId) throws IOException, InterruptedException, AdminApiException {
        return read(apiClient.get("/api/admin/comments/" + commentId), new TypeReference<AdminCommentDto>() {});
    }

    public void deleteComment(long commentId, String reason)
            throws IOException, InterruptedException, AdminApiException {
        ensureSuccess(apiClient.delete("/api/admin/comments/" + commentId + reasonQuery(reason)));
    }

    // ===== Граф =====

    public GraphDataDto getGraph(boolean leiden) throws IOException, InterruptedException, AdminApiException {
        return read(apiClient.get("/api/admin/graph?leiden=" + leiden), new TypeReference<GraphDataDto>() {});
    }

    /** Розмір і ехо-показник кожної значущої спільноти (для легенди графа). */
    public List<CommunityInfo> getCommunityAnalysis(boolean leiden)
            throws IOException, InterruptedException, AdminApiException {
        ApiClient.ApiResponse response = apiClient.get("/api/graph/communities/analysis?leiden=" + leiden);
        ensureSuccess(response);
        List<CommunityInfo> result = new ArrayList<>();
        for (JsonNode node : mapper.readTree(response.body())) {
            result.add(new CommunityInfo(
                    node.path("community").asLong(),
                    node.path("memberCount").asInt(),
                    node.path("echoChamberScore").asDouble()));
        }
        return result;
    }

    /** Перенести актуальних користувачів і підписки з PostgreSQL у Neo4j. */
    public GraphSyncResultDto syncGraph() throws IOException, InterruptedException, AdminApiException {
        return read(apiClient.post("/api/graph/sync", null), new TypeReference<GraphSyncResultDto>() {});
    }

    /** Заново знайти спільноти алгоритмом Louvain або Leiden. */
    public CommunityDetectionResultDto detectCommunities(boolean leiden)
            throws IOException, InterruptedException, AdminApiException {
        String path = leiden ? "/api/graph/communities/leiden" : "/api/graph/communities";
        return read(apiClient.post(path, null), new TypeReference<CommunityDetectionResultDto>() {});
    }

    public record CommunityInfo(long community, int memberCount, double echoChamberScore) {
    }

    // ===== Журнал =====

    public List<AuditLogEntryDto> getAuditLog(int limit) throws IOException, InterruptedException, AdminApiException {
        return read(apiClient.get("/api/admin/audit?limit=" + limit),
                new TypeReference<List<AuditLogEntryDto>>() {});
    }

    // ===== Спільне =====

    private <T> T read(ApiClient.ApiResponse response, TypeReference<T> type) throws IOException, AdminApiException {
        if (!response.isSuccess()) {
            throw new AdminApiException(response.statusCode(), errorText(response));
        }
        return mapper.readValue(response.body(), type);
    }

    /** Перевірка відповіді без тіла (204 No Content). */
    void ensureSuccess(ApiClient.ApiResponse response) throws AdminApiException {
        if (!response.isSuccess()) {
            throw new AdminApiException(response.statusCode(), errorText(response));
        }
    }

    /** Текст у URL: кирилиця й пробіли кодуються (%D0%...). */
    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String reasonQuery(String reason) {
        return reason == null || reason.isBlank() ? "" : "?reason=" + encode(reason.trim());
    }

    private String errorText(ApiClient.ApiResponse response) {
        try {
            JsonNode node = mapper.readTree(response.body());
            if (node != null && node.hasNonNull("error")) {
                return node.get("error").asText();
            }
        } catch (IOException ignored) {
            // тіло не JSON — покажемо лише код
        }
        return "Помилка сервера (" + response.statusCode() + ")";
    }

    /** Помилка, яку повернув сервер (4xx/5xx), з людським текстом. */
    public static class AdminApiException extends Exception {
        private final int status;

        public AdminApiException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }
}