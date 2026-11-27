package com.example.social.client.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Обрані теми (холодний старт стрічки) і сторінка «Моя стрічка». */
public class InsightsApiService {

    private final ApiClient apiClient;

    public InsightsApiService(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /** Тема на вибір і скільки в мережі постів з нею. */
    public record TagOption(String tag, long posts) {
    }

    /**
     * @param needsOnboarding запропонувати вибір тем зараз (людину ще не питали, і вподобань / підписок у неї немає)
     */
    public record Interests(List<String> selected, List<TagOption> options, boolean needsOnboarding, int maxInterests) {
    }

    public Interests getInterests() throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get("/api/users/me/interests");
        if (!response.isSuccess()) {
            throw new IOException("Не вдалося завантажити теми (код " + response.statusCode() + ")");
        }
        return parseInterests(apiClient.getObjectMapper().readTree(response.body()));
    }

    /** Порожній список — «пропустити»: вибір тем більше не з'являтиметься сам. */
    public Interests saveInterests(List<String> tags) throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.put("/api/users/me/interests", Map.of("tags", tags));
        JsonNode node = apiClient.getObjectMapper().readTree(response.body());
        if (!response.isSuccess()) {
            throw new IOException(node.has("error") ? node.get("error").asText() : "Не вдалося зберегти теми");
        }
        return parseInterests(node);
    }

    /** Дані сторінки «Моя стрічка» як є (розбирає AnalyticsController): стрічка рахується на сервері — до хвилини. */
    public JsonNode getInsights() throws IOException, InterruptedException {
        ApiClient.ApiResponse response = apiClient.get("/api/users/me/insights", Duration.ofSeconds(60));
        if (!response.isSuccess()) {
            throw new IOException("Не вдалося завантажити аналітику (код " + response.statusCode() + ")");
        }
        return apiClient.getObjectMapper().readTree(response.body());
    }

    private static Interests parseInterests(JsonNode node) {
        List<String> selected = new ArrayList<>();
        node.path("selected").forEach(t -> selected.add(t.asText()));
        List<TagOption> options = new ArrayList<>();
        node.path("options").forEach(o -> options.add(new TagOption(o.path("tag").asText(), o.path("posts").asLong())));
        return new Interests(selected, options, node.path("needsOnboarding").asBoolean(false),
                node.path("maxInterests").asInt(10));
    }
}
