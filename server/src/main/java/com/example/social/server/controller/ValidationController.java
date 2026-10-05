package com.example.social.server.controller;

import com.example.social.server.service.EchoChamberValidationService;
import com.example.social.server.service.RecommendationWeightExperimentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/dev/validation")
public class ValidationController {

    private final EchoChamberValidationService echoService;
    private final RecommendationWeightExperimentService weightService;

    public ValidationController(EchoChamberValidationService echoService,
                                RecommendationWeightExperimentService weightService) {
        this.echoService = echoService;
        this.weightService = weightService;
    }

    @PostMapping("/echo-chamber")
    public ResponseEntity<?> echoChamber(
            @RequestParam(name = "repeats", defaultValue = "5") int repeats,
            @RequestParam(name = "usersPerCommunity", defaultValue = "6") int usersPerCommunity,
            @RequestParam(name = "postsPerUser", defaultValue = "8") int postsPerUser,
            @RequestParam(name = "seed", defaultValue = "42") long seed) {
        return wrap(() -> echoService.run(repeats, usersPerCommunity, postsPerUser, seed));
    }

    @DeleteMapping("/echo-chamber/leftovers")
    public ResponseEntity<?> echoCleanup() {
        return ResponseEntity.ok(echoService.cleanupLeftovers());
    }

    @PostMapping("/recommendation-weights")
    public ResponseEntity<?> recommendationWeights(
            @RequestParam(name = "users", defaultValue = "90") int users,
            @RequestParam(name = "postsPerUser", defaultValue = "4") int postsPerUser,
            @RequestParam(name = "likesPerUser", defaultValue = "30") int likesPerUser,
            @RequestParam(name = "followsPerUser", defaultValue = "6") int followsPerUser,
            @RequestParam(name = "hiddenShare", defaultValue = "0.2") double hiddenShare,
            @RequestParam(name = "recencyTauDays", defaultValue = "0") double recencyTauDays,
            @RequestParam(name = "seed", defaultValue = "42") long seed,
            @RequestParam(name = "k", defaultValue = "10") int k,
            @RequestParam(name = "gridSteps", defaultValue = "20") int gridSteps,
            @RequestParam(name = "includeGrid", defaultValue = "true") boolean includeGrid,
            @RequestParam(name = "candidate", required = false) String candidate) {
        double[] candidateWeights = null;
        if (candidate != null && !candidate.isBlank()) {
            try {
                candidateWeights = Arrays.stream(candidate.split(","))
                        .map(String::trim).mapToDouble(Double::parseDouble).toArray();
            } catch (NumberFormatException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "error", "candidate: п'ять чисел через кому, напр. 0.1,0.35,0.35,0.1,0.1"));
            }
        }
        final double[] cw = candidateWeights;
        return wrap(() -> weightService.run(new RecommendationWeightExperimentService.Params(
                users, postsPerUser, likesPerUser, followsPerUser, hiddenShare,
                recencyTauDays, seed, k, gridSteps, includeGrid, cw)));
    }
    @DeleteMapping("/recommendation-weights/leftovers")
    public ResponseEntity<?> weightsCleanup() {
        return ResponseEntity.ok(weightService.cleanupLeftovers());
    }

    private ResponseEntity<?> wrap(java.util.function.Supplier<Map<String, Object>> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}