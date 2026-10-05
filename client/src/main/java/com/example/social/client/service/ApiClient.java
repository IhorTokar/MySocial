package com.example.social.client.service;

import com.example.social.client.util.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

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

    public ApiResponse patch(String path, Object body) throws IOException, InterruptedException {
        return send("PATCH", path, body, true);
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
            case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(json));
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

    public ApiResponse postMultipart(String path, String fieldName, Path file)
            throws IOException, InterruptedException {

        String boundary = "----SocialBoundary" + UUID.randomUUID();

        String fileName = file.getFileName().toString();
        String extension = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase() : "";

        // Files.probeContentType на Linux ненадійний, тому визначаємо тип за розширенням
        String contentType = switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
        // ім'я файлу лише ASCII, щоб кирилиця в назві не ламала заголовок
        String safeName = "upload" + (extension.isEmpty() ? "" : "." + extension);

        byte[] head = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + safeName + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] content = Files.readAllBytes(file);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream body = new ByteArrayOutputStream(head.length + content.length + tail.length);
        body.writeBytes(head);
        body.writeBytes(content);
        body.writeBytes(tail);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));

        if (SessionManager.getInstance().isLoggedIn()) {
            builder.header("Authorization", "Bearer " + SessionManager.getInstance().getToken());
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new ApiResponse(response.statusCode(), response.body());
    }

    public ApiResponse deleteWithBody(String path, Object body) throws IOException, InterruptedException {
        String json = body != null ? objectMapper.writeValueAsString(body) : "";

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .method("DELETE", HttpRequest.BodyPublishers.ofString(json));

        if (SessionManager.getInstance().isLoggedIn()) {
            builder.header("Authorization", "Bearer " + SessionManager.getInstance().getToken());
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new ApiResponse(response.statusCode(), response.body());
    }
}