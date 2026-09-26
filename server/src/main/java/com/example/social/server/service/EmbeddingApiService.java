package com.example.social.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class EmbeddingApiService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingApiService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public EmbeddingApiService(@Value("${nlp-service.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(15000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public List<Double> embed(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            String jsonBody = objectMapper.writeValueAsString(Map.of("text", text));
            log.debug("Sending to NLP service: {}", jsonBody);

            String rawResponse = restClient.post()
                    .uri("/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            log.debug("NLP service raw response length: {}", rawResponse != null ? rawResponse.length() : -1);

            Map<String, Object> parsed = objectMapper.readValue(rawResponse, Map.class);
            @SuppressWarnings("unchecked")
            List<Double> vector = (List<Double>) parsed.get("vector");
            return vector;
        } catch (Exception e) {
            log.warn("Failed to get embedding from NLP service: {}", e.getMessage(), e);
            return null;
        }
    }
}