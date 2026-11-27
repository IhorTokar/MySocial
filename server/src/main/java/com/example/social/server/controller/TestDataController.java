package com.example.social.server.controller;

import com.example.social.server.exception.BadRequestException;
import com.example.social.server.repository.PostEmbeddingRepository;
import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.EmbeddingApiService;
import com.example.social.server.service.GraphSyncService;
import com.example.social.server.service.PostSentimentService;
import com.example.social.server.service.RecommendationService;
import com.example.social.server.service.SeedEvolutionService;
import com.example.social.server.service.SeedGeneratorService;
import com.example.social.server.service.TestDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dev")
public class TestDataController {

    private final TestDataService testDataService;
    private final EmbeddingApiService embeddingApiService;
    private final PostEmbeddingRepository postEmbeddingRepository;
    private final PostSentimentService postSentimentService;
    private final RecommendationService recommendationService;
    private final SeedGeneratorService seedGeneratorService;
    private final GraphSyncService graphSyncService;
    private final SeedEvolutionService seedEvolutionService;

    public TestDataController(TestDataService testDataService, EmbeddingApiService embeddingApiService,
                              PostEmbeddingRepository postEmbeddingRepository,
                              PostSentimentService postSentimentService,
                              RecommendationService recommendationService,
                              SeedGeneratorService seedGeneratorService,
                              GraphSyncService graphSyncService,
                              SeedEvolutionService seedEvolutionService) {
        this.testDataService = testDataService;
        this.embeddingApiService = embeddingApiService;
        this.postEmbeddingRepository = postEmbeddingRepository;
        this.postSentimentService = postSentimentService;
        this.recommendationService = recommendationService;
        this.seedGeneratorService = seedGeneratorService;
        this.graphSyncService = graphSyncService;
        this.seedEvolutionService = seedEvolutionService;
    }

    /**
     * Реалістична мережа: спільноти з інтересами, гомофільні підписки, тематичні пости з тональністю,
     * вподобання за інтересами, коментарі й відповіді, дати за останні days днів.
     * Приклад: POST /api/dev/seed-realistic?users=300&communities=8&days=45
     */
    @PostMapping("/seed-realistic")
    public ResponseEntity<?> seedRealistic(
            @RequestParam(name = "users", defaultValue = "200") int users,
            @RequestParam(name = "communities", defaultValue = "8") int communities,
            @RequestParam(name = "days", defaultValue = "45") int days,
            @RequestParam(name = "avgPosts", defaultValue = "6") double avgPosts,
            @RequestParam(name = "avgFollows", defaultValue = "12") double avgFollows,
            @RequestParam(name = "avgLikes", defaultValue = "30") double avgLikes,
            @RequestParam(name = "homophily", defaultValue = "0.8") double homophily,
            @RequestParam(name = "narrowShare", defaultValue = "0.5") double narrowShare,
            @RequestParam(name = "bridgeShare", defaultValue = "0.1") double bridgeShare,
            @RequestParam(name = "commentRate", defaultValue = "0.15") double commentRate,
            @RequestParam(name = "replyRate", defaultValue = "0.4") double replyRate,
            @RequestParam(name = "saveRate", defaultValue = "0.05") double saveRate,
            @RequestParam(name = "seed", defaultValue = "42") long seed) {
        Map<String, Object> result = new LinkedHashMap<>(seedGeneratorService.generate(new SeedGeneratorService.Params(
                users, communities, days, avgPosts, avgFollows, avgLikes, homophily, narrowShare, bridgeShare,
                commentRate, replyRate, saveRate, seed)));
        result.put("graphSync", syncGraph());
        return ResponseEntity.ok(result);
    }

    /**
     * Мережа, що живе в часі: базова мережа (як /seed-realistic, але її «сьогодні» — epochs тижнів тому),
     * далі epochs тижнів активності з ролями — затягування в ехо-камеру, вихід з неї, міграція між спільнотами,
     * новачки й ті, хто йде. Після бази й кожного тижня — тональність, вектори й знімок ехо-камер.
     * Триває кілька хвилин. Видаляється тим самим DELETE /seed-realistic.
     * Приклад: POST /api/dev/seed-evolution?users=200&days=21&epochs=6
     *
     * @param snapshots false — лише дані, без NLP і знімків (коли Neo4j чи NLP-сервіс не запущено)
     */
    @PostMapping("/seed-evolution")
    public ResponseEntity<?> seedEvolution(
            @AuthenticationPrincipal AuthenticatedUser admin,
            @RequestParam(name = "users", defaultValue = "200") int users,
            @RequestParam(name = "communities", defaultValue = "8") int communities,
            @RequestParam(name = "days", defaultValue = "21") int days,
            @RequestParam(name = "avgPosts", defaultValue = "6") double avgPosts,
            @RequestParam(name = "avgFollows", defaultValue = "12") double avgFollows,
            @RequestParam(name = "avgLikes", defaultValue = "30") double avgLikes,
            @RequestParam(name = "homophily", defaultValue = "0.8") double homophily,
            @RequestParam(name = "narrowShare", defaultValue = "0.5") double narrowShare,
            @RequestParam(name = "bridgeShare", defaultValue = "0.1") double bridgeShare,
            @RequestParam(name = "commentRate", defaultValue = "0.15") double commentRate,
            @RequestParam(name = "replyRate", defaultValue = "0.4") double replyRate,
            @RequestParam(name = "saveRate", defaultValue = "0.05") double saveRate,
            @RequestParam(name = "seed", defaultValue = "42") long seed,
            @RequestParam(name = "epochs", defaultValue = "6") int epochs,
            @RequestParam(name = "epochDays", defaultValue = "7") int epochDays,
            @RequestParam(name = "drifterShare", defaultValue = "0.05") double drifterShare,
            @RequestParam(name = "escaperShare", defaultValue = "0.05") double escaperShare,
            @RequestParam(name = "migrantShare", defaultValue = "0.05") double migrantShare,
            @RequestParam(name = "newcomerShare", defaultValue = "0.1") double newcomerShare,
            @RequestParam(name = "leaverShare", defaultValue = "0.05") double leaverShare,
            @RequestParam(name = "snapshots", defaultValue = "true") boolean snapshots) {
        SeedGeneratorService.Params network = new SeedGeneratorService.Params(
                users, communities, days, avgPosts, avgFollows, avgLikes, homophily, narrowShare, bridgeShare,
                commentRate, replyRate, saveRate, seed);
        Map<String, Object> result = new LinkedHashMap<>(seedEvolutionService.run(new SeedEvolutionService.Params(
                        network, epochs, epochDays, drifterShare, escaperShare, migrantShare, newcomerShare, leaverShare),
                admin.getUserId(), snapshots));
        if (!snapshots) {
            result.put("graphSync", syncGraph());   // зі знімками граф оновлює кожен знімок
        }
        return ResponseEntity.ok(result);
    }

    /** Видалити всі акаунти, створені /seed-realistic і /seed-evolution (разом з усім їхнім вмістом). */
    @DeleteMapping("/seed-realistic")
    public ResponseEntity<?> cleanupRealistic() {
        Map<String, Object> result = new LinkedHashMap<>(seedGeneratorService.cleanup());
        result.put("graphSync", syncGraph());
        return ResponseEntity.ok(result);
    }

    /** Граф у Neo4j — після коміту в PostgreSQL; без Neo4j генерація однаково вдала. */
    private Object syncGraph() {
        try {
            return graphSyncService.syncAll();
        } catch (RuntimeException e) {
            return "не синхронізовано: " + e.getMessage();
        }
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

    /** Вектори постів без них; all=true — перерахувати для всіх (напр., після зміни тексту для вектора). */
    @PostMapping("/backfill-embeddings")
    public ResponseEntity<?> backfillEmbeddings(@RequestParam(name = "all", defaultValue = "false") boolean all) {
        return ResponseEntity.ok(testDataService.backfillEmbeddings(all));
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