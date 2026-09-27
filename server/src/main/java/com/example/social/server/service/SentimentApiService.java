package com.example.social.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class SentimentApiService {

    private static final Logger log = LoggerFactory.getLogger(SentimentApiService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public SentimentApiService(@Value("${nlp-service.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(15000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Повертає тональність тексту в [-1, 1], або null, якщо NLP-сервіс недоступний.
     */
    public Double analyze(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            String jsonBody = objectMapper.writeValueAsString(Map.of("text", text));

            String rawResponse = restClient.post()
                    .uri("/sentiment")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            Map<String, Object> parsed = objectMapper.readValue(rawResponse, Map.class);
            Object sentiment = parsed.get("sentiment");
            return sentiment != null ? ((Number) sentiment).doubleValue() : null;
        } catch (Exception e) {
            log.warn("Failed to get sentiment from NLP service: {}", e.getMessage());
            return null;
        }
    }
}