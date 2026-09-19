package com.example.social.client.service;

import com.example.social.client.util.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ApiClient {

    public static final String BASE_URL = "http://localhost:8080";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // підтримка LocalDateTime тощо
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public ApiResponse post(String path, Object body) throws IOException, InterruptedException {
        return send("POST", path, body, true);
    }

    public ApiResponse postPublic(String path, Object body) throws IOException, InterruptedException {
        return send("POST", path, body, false);
    }

    public ApiResponse get(String path) throws IOException, InterruptedException {
        return send("GET", path, null, true);
    }

    public ApiResponse delete(String path) throws IOException, InterruptedException {
        return send("DELETE", path, null, true);
    }

    private ApiResponse send(String method, String path, Object body, boolean withAuth)
            throws IOException, InterruptedException {

        String json = body != null ? objectMapper.writeValueAsString(body) : "";

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15));

        if (withAuth && SessionManager.getInstance().isLoggedIn()) {
            builder.header("Authorization", "Bearer " + SessionManager.getInstance().getToken());
        }

        switch (method) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(json));
            case "DELETE" -> builder.DELETE();
            default -> builder.GET();
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new ApiResponse(response.statusCode(), response.body());
    }

    public record ApiResponse(int statusCode, String body) {
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}