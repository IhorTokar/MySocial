package com.example.social.server.controller;

import com.example.social.server.repository.PostEmbeddingRepository;
import com.example.social.server.service.EmbeddingApiService;
import com.example.social.server.service.TestDataService;
import com.example.social.server.service.PostSentimentService;
import com.example.social.server.exception.BadRequestException;
import com.example.social.server.service.RecommendationService;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dev")
public class TestDataController {

    private final TestDataService testDataService;
    private final EmbeddingApiService embeddingApiService;
    private final PostEmbeddingRepository postEmbeddingRepository;
    private final PostSentimentService postSentimentService;
    private final RecommendationService recommendationService;

    public TestDataController(TestDataService testDataService, EmbeddingApiService embeddingApiService,
                              PostEmbeddingRepository postEmbeddingRepository,
                              PostSentimentService postSentimentService,
                              RecommendationService recommendationService) {
        this.testDataService = testDataService;
        this.embeddingApiService = embeddingApiService;
        this.postEmbeddingRepository = postEmbeddingRepository;
        this.postSentimentService = postSentimentService;
        this.recommendationService = recommendationService;
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

    /** Тональність для постів без неї; retryLexicon=true — також перерахувати оцінені словником. */
    @PostMapping("/backfill-sentiment")
    public ResponseEntity<?> backfillSentiment(
            @RequestParam(name = "retryLexicon", defaultValue = "false") boolean retryLexicon) {
        return ResponseEntity.ok(postSentimentService.backfill(retryLexicon));
    }

    @PostMapping("/randomize-usernames")
    public ResponseEntity<?> randomizeUsernames() {
        return ResponseEntity.ok(testDataService.randomizeSeedUsernames());
    }

    /**
     * MMR-експеримент: стрічка користувача при різних λ на тих самих кандидатах.
     * Приклад: /api/dev/mmr-experiment?userId=83&limit=10&lambdas=1,0.9,0.8,0.7,0.5,0.3
     */
    @GetMapping("/mmr-experiment")
    public ResponseEntity<?> mmrExperiment(
            @RequestParam(name = "userId") Long userId,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "lambdas", defaultValue = "1,0.9,0.8,0.7,0.5,0.3") List<Double> lambdas) {
        if (lambdas.stream().anyMatch(l -> l < 0 || l > 1)) {
            throw new BadRequestException("Кожне λ має бути в межах [0, 1]");
        }
        return ResponseEntity.ok(recommendationService.runMmrExperiment(userId, limit, lambdas));
    }
}