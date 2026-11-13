package com.example.social.client.service;

import com.example.social.shared.dto.NotificationDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.util.List;

/** Сповіщення: список, лічильник непрочитаних, «прочитано». */
public class NotificationApiService {

    private final ApiClient apiClient;

    public NotificationApiService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public List<NotificationDto> latest(int limit) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get("/api/notifications?limit=" + limit);
        ensureSuccess(response);
        return apiClient.getObjectMapper().readValue(response.body(), new TypeReference<>() {});
    }

    public long unreadCount() throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get("/api/notifications/unread-count");
        ensureSuccess(response);
        return apiClient.getObjectMapper().readTree(response.body()).path("unread").asLong();
    }

    public void markSeen() throws IOException, InterruptedException {
        ensureSuccess(apiClient.post("/api/notifications/seen", null));
    }

    private static void ensureSuccess(ApiClient.ApiResponse response) throws IOException {
        if (!response.isSuccess()) {
            throw new IOException("сервер відповів " + response.statusCode());
        }
    }
}