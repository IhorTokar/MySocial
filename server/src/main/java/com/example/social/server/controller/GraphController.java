package com.example.social.server.controller;

import com.example.social.server.exception.NotFoundException;
import com.example.social.server.service.*;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final Driver neo4jDriver;
    private final GraphSyncService graphSyncService;
    private final GraphEmbeddingService graphEmbeddingService;
    private final CommunityDetectionService communityDetectionService;
    private final UserSimilarityService userSimilarityService;
    private final CommunityAnalysisService communityAnalysisService;

    public GraphController(Driver neo4jDriver,
                           GraphSyncService graphSyncService,
                           GraphEmbeddingService graphEmbeddingService,
                           CommunityDetectionService communityDetectionService,
                           UserSimilarityService userSimilarityService,
                           CommunityAnalysisService communityAnalysisService) {
        this.neo4jDriver = neo4jDriver;
        this.graphSyncService = graphSyncService;
        this.graphEmbeddingService = graphEmbeddingService;
        this.communityDetectionService = communityDetectionService;
        this.userSimilarityService = userSimilarityService;
        this.communityAnalysisService = communityAnalysisService;
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        try (Session session = neo4jDriver.session()) {
            String greeting = session.run("RETURN 'Neo4j connection works' AS message")
                    .single()
                    .get("message")
                    .asString();
            return ResponseEntity.ok(Map.of("status", "ok", "neo4j", greeting));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/communities")
    public ResponseEntity<?> detectCommunities() {
        return ResponseEntity.ok(communityDetectionService.detectCommunities());
    }

    @GetMapping("/communities")
    public ResponseEntity<?> getCommunities() {
        return ResponseEntity.ok(communityDetectionService.getCommunities());
    }

    @GetMapping("/recommendations/{userId}")
    public ResponseEntity<?> getRecommendations(@PathVariable("userId") Long userId,
                                                @RequestParam(value = "topN", defaultValue = "5") int topN,
                                                @RequestParam(value = "excludeFollowed", defaultValue = "true") boolean excludeFollowed) {
        return ResponseEntity.ok(userSimilarityService.getSimilarUsers(userId, topN, excludeFollowed));
    }

    @GetMapping("/communities/summary")
    public ResponseEntity<?> getCommunitySummary() {
        return ResponseEntity.ok(communityDetectionService.getCommunitySummary());
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync() {
        return ResponseEntity.ok(graphSyncService.syncAll());
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        return ResponseEntity.ok(graphSyncService.getGraphStats());
    }

    @PostMapping("/embeddings")
    public ResponseEntity<?> generateEmbeddings(@RequestParam(value = "dimension", defaultValue = "16") int dimension) {
        return ResponseEntity.ok(graphEmbeddingService.generateEmbeddings(dimension));
    }

    @GetMapping("/embeddings")
    public ResponseEntity<?> getEmbeddings() {
        return ResponseEntity.ok(graphEmbeddingService.getEmbeddings());
    }

    @GetMapping("/communities/analysis")
    public ResponseEntity<?> analyzeCommunities(@RequestParam(defaultValue = "false") boolean leiden) {
        return ResponseEntity.ok(communityAnalysisService.analyzeCommunities(leiden));
    }

    @PostMapping("/communities/leiden")
    public ResponseEntity<?> detectCommunitiesLeiden() {
        return ResponseEntity.ok(communityDetectionService.detectCommunitiesLeiden());
    }

    @GetMapping("/communities/leiden")
    public ResponseEntity<?> getCommunitiesLeiden() {
        return ResponseEntity.ok(communityDetectionService.getCommunitiesLeiden());
    }

    @GetMapping("/communities/leiden/summary")
    public ResponseEntity<?> getCommunitySummaryLeiden() {
        return ResponseEntity.ok(communityDetectionService.getCommunitySummaryLeiden());
    }

    @GetMapping("/communities/connectivity")
    public ResponseEntity<?> checkConnectivityLouvain() {
        return ResponseEntity.ok(communityDetectionService.checkConnectivityLouvain());
    }

    @GetMapping("/communities/leiden/connectivity")
    public ResponseEntity<?> checkConnectivityLeiden() {
        return ResponseEntity.ok(communityDetectionService.checkConnectivityLeiden());
    }

    @GetMapping("/community/{userId}")
    public ResponseEntity<?> getUserCommunity(@PathVariable("userId") Long userId,
                                              @RequestParam(defaultValue = "false") boolean leiden) {
        return ResponseEntity.ok(communityAnalysisService.getCommunityForUser(userId, leiden)
                .orElseThrow(() -> new NotFoundException(
                        "Спільнота ще не визначена або юзер поза значущою спільнотою")));
    }
}