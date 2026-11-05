package com.example.social.server.controller;

import com.example.social.server.exception.NotFoundException;
import com.example.social.server.service.*;
import com.example.social.shared.dto.*;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public ResponseEntity<CommunityDetectionResultDto> detectCommunities() {
        return ResponseEntity.ok(communityDetectionService.detectCommunities());
    }

    @GetMapping("/communities")
    public ResponseEntity<List<CommunityMemberDto>> getCommunities() {
        return ResponseEntity.ok(communityDetectionService.getCommunities());
    }

    @GetMapping("/recommendations/{userId}")
    public ResponseEntity<List<SimilarUserDto>> getRecommendations(@PathVariable("userId") Long userId,
                                                                   @RequestParam(value = "topN", defaultValue = "5") int topN,
                                                                   @RequestParam(value = "excludeFollowed", defaultValue = "true") boolean excludeFollowed) {
        return ResponseEntity.ok(userSimilarityService.getSimilarUsers(userId, topN, excludeFollowed));
    }

    @GetMapping("/communities/summary")
    public ResponseEntity<CommunitySummaryDto> getCommunitySummary() {
        return ResponseEntity.ok(communityDetectionService.getCommunitySummary());
    }

    @PostMapping("/sync")
    public ResponseEntity<GraphSyncResultDto> sync() {
        return ResponseEntity.ok(graphSyncService.syncAll());
    }

    @GetMapping("/stats")
    public ResponseEntity<GraphStatsDto> stats() {
        return ResponseEntity.ok(graphSyncService.getGraphStats());
    }

    @PostMapping("/embeddings")
    public ResponseEntity<EmbeddingResultDto> generateEmbeddings(@RequestParam(value = "dimension", defaultValue = "16") int dimension) {
        return ResponseEntity.ok(graphEmbeddingService.generateEmbeddings(dimension));
    }

    @GetMapping("/embeddings")
    public ResponseEntity<List<UserEmbeddingDto>> getEmbeddings() {
        return ResponseEntity.ok(graphEmbeddingService.getEmbeddings());
    }

    @GetMapping("/communities/analysis")
    public ResponseEntity<List<CommunityAnalysisDto>> analyzeCommunities(@RequestParam(defaultValue = "false") boolean leiden) {
        return ResponseEntity.ok(communityAnalysisService.analyzeCommunities(leiden));
    }

    @PostMapping("/communities/leiden")
    public ResponseEntity<CommunityDetectionResultDto> detectCommunitiesLeiden() {
        return ResponseEntity.ok(communityDetectionService.detectCommunitiesLeiden());
    }

    @GetMapping("/communities/leiden")
    public ResponseEntity<List<CommunityMemberDto>> getCommunitiesLeiden() {
        return ResponseEntity.ok(communityDetectionService.getCommunitiesLeiden());
    }

    @GetMapping("/communities/leiden/summary")
    public ResponseEntity<CommunitySummaryDto> getCommunitySummaryLeiden() {
        return ResponseEntity.ok(communityDetectionService.getCommunitySummaryLeiden());
    }

    @GetMapping("/communities/connectivity")
    public ResponseEntity<List<CommunityConnectivityDto>> checkConnectivityLouvain() {
        return ResponseEntity.ok(communityDetectionService.checkConnectivityLouvain());
    }

    @GetMapping("/communities/leiden/connectivity")
    public ResponseEntity<List<CommunityConnectivityDto>> checkConnectivityLeiden() {
        return ResponseEntity.ok(communityDetectionService.checkConnectivityLeiden());
    }

    @GetMapping("/community/{userId}")
    public ResponseEntity<CommunityAnalysisDto> getUserCommunity(@PathVariable("userId") Long userId,
                                                                 @RequestParam(defaultValue = "false") boolean leiden) {
        return ResponseEntity.ok(communityAnalysisService.getCommunityForUser(userId, leiden)
                .orElseThrow(() -> new NotFoundException(
                        "Спільнота ще не визначена або юзер поза значущою спільнотою")));
    }
}