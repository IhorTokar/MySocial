package com.example.social.client.service;

import com.example.social.shared.dto.SuggestedUserDto;
import com.example.social.shared.dto.TrendingTagDto;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.util.List;

/** Бічна панель: гарячі теми й кого почитати. */
public class DiscoverApiService {

    private final ApiClient apiClient;

    public DiscoverApiService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public List<TrendingTagDto> getTrends(int limit) throws IOException, InterruptedException {
        return read(apiClient.get("/api/discover/trends?limit=" + limit), new TypeReference<>() {});
    }

    public List<SuggestedUserDto> getSuggestedPeople(int limit) throws IOException, InterruptedException {
        return read(apiClient.get("/api/discover/people?limit=" + limit), new TypeReference<>() {});
    }

    private <T> T read(ApiClient.ApiResponse response, TypeReference<T> type) throws IOException {
        if (!response.isSuccess()) {
            throw new IOException("сервер відповів " + response.statusCode());
        }
        return apiClient.getObjectMapper().readValue(response.body(), type);
    }
}