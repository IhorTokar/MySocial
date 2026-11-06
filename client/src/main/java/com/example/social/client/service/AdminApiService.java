package com.example.social.client.service;

import com.example.social.shared.dto.AuditLogEntryDto;
import com.example.social.shared.dto.RecommendationWeightsDto;
import com.example.social.shared.dto.WeightsUpdateRequestDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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