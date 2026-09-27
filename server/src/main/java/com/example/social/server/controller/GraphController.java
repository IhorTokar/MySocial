package com.example.social.server.controller;

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
        try {
            return ResponseEntity.ok(communityDetectionService.detectCommunities());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/communities")
    public ResponseEntity<?> getCommunities() {
        try {
            return ResponseEntity.ok(communityDetectionService.getCommunities());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/recommendations/{userId}")
    public ResponseEntity<?> getRecommendations(@PathVariable("userId") Long userId,
                                                @RequestParam(value = "topN", defaultValue = "5") int topN,
                                                @RequestParam(value = "excludeFollowed", defaultValue = "true") boolean excludeFollowed) {
        try {
            return ResponseEntity.ok(userSimilarityService.getSimilarUsers(userId, topN, excludeFollowed));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/communities/summary")
    public ResponseEntity<?> getCommunitySummary() {
        try {
            return ResponseEntity.ok(communityDetectionService.getCommunitySummary());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/sync")
    public ResponseEntity<?> sync() {
        try {
            return ResponseEntity.ok(graphSyncService.syncAll());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        try {
            return ResponseEntity.ok(graphSyncService.getGraphStats());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/embeddings")
    public ResponseEntity<?> generateEmbeddings(@RequestParam(value = "dimension", defaultValue = "16") int dimension) {
        try {
            return ResponseEntity.ok(graphEmbeddingService.generateEmbeddings(dimension));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/embeddings")
    public ResponseEntity<?> getEmbeddings() {
        try {
            return ResponseEntity.ok(graphEmbeddingService.getEmbeddings());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/communities/analysis")
    public ResponseEntity<?> analyzeCommunities(@RequestParam(defaultValue = "false") boolean leiden) {
        try {
            return ResponseEntity.ok(communityAnalysisService.analyzeCommunities(leiden));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/communities/leiden")
    public ResponseEntity<?> detectCommunitiesLeiden() {
        try {
            return ResponseEntity.ok(communityDetectionService.detectCommunitiesLeiden());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/communities/leiden")
    public ResponseEntity<?> getCommunitiesLeiden() {
        try {
            return ResponseEntity.ok(communityDetectionService.getCommunitiesLeiden());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/communities/leiden/summary")
    public ResponseEntity<?> getCommunitySummaryLeiden() {
        try {
            return ResponseEntity.ok(communityDetectionService.getCommunitySummaryLeiden());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/communities/connectivity")
    public ResponseEntity<?> checkConnectivityLouvain() {
        try {
            return ResponseEntity.ok(communityDetectionService.checkConnectivityLouvain());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/communities/leiden/connectivity")
    public ResponseEntity<?> checkConnectivityLeiden() {
        try {
            return ResponseEntity.ok(communityDetectionService.checkConnectivityLeiden());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}