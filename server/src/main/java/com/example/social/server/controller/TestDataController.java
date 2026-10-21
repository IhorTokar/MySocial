package com.example.social.server.controller;

import com.example.social.server.repository.PostEmbeddingRepository;
import com.example.social.server.service.EmbeddingApiService;
import com.example.social.server.service.TestDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        return ResponseEntity.ok(testDataService.generateRandomPosts(count));
    }

    @PostMapping("/seed")
    public ResponseEntity<?> seed(@RequestParam(value = "users", defaultValue = "20") int users,
                                  @RequestParam(value = "maxFollows", defaultValue = "5") int maxFollows) {
        return ResponseEntity.ok(testDataService.generateTestData(users, maxFollows));
    }

    @PostMapping("/seed-likes")
    public ResponseEntity<?> seedLikes(@RequestParam(name = "count", defaultValue = "200") int count) {
        return ResponseEntity.ok(testDataService.generateRandomLikes(count));
    }

    @PostMapping("/backfill-embeddings")
    public ResponseEntity<?> backfillEmbeddings() {
        return ResponseEntity.ok(testDataService.backfillEmbeddings());
    }

    @PostMapping("/randomize-usernames")
    public ResponseEntity<?> randomizeUsernames() {
        return ResponseEntity.ok(testDataService.randomizeSeedUsernames());
    }
}