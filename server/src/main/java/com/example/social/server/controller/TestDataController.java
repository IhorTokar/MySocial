package com.example.social.server.controller;

import com.example.social.server.repository.PostEmbeddingRepository;
import com.example.social.server.service.EmbeddingApiService;
import com.example.social.server.service.TestDataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dev")
public class TestDataController {

    private final TestDataService testDataService;
    private final EmbeddingApiService embeddingApiService;
    private final PostEmbeddingRepository postEmbeddingRepository;

    public TestDataController(TestDataService testDataService, EmbeddingApiService embeddingApiService, PostEmbeddingRepository postEmbeddingRepository) {
        this.testDataService = testDataService;
        this.embeddingApiService = embeddingApiService;
        this.postEmbeddingRepository = postEmbeddingRepository;
    }

    @PostMapping("/seed-posts")
    public ResponseEntity<?> seedPosts(@RequestParam(name = "count", defaultValue = "50") int count) {
        try {
            return ResponseEntity.ok(testDataService.generateRandomPosts(count));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/seed")
    public ResponseEntity<?> seed(@RequestParam(value = "users", defaultValue = "20") int users,
                                  @RequestParam(value = "maxFollows", defaultValue = "5") int maxFollows) {
        try {
            return ResponseEntity.ok(testDataService.generateTestData(users, maxFollows));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/seed-likes")
    public ResponseEntity<?> seedLikes(@RequestParam(name = "count", defaultValue = "200") int count) {
        try {
            return ResponseEntity.ok(testDataService.generateRandomLikes(count));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/backfill-embeddings")
    public ResponseEntity<?> backfillEmbeddings() {
        try {
            return ResponseEntity.ok(testDataService.backfillEmbeddings());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}