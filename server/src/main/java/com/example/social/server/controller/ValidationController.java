package com.example.social.server.controller;

import com.example.social.server.exception.BadRequestException;
import com.example.social.server.service.EchoChamberValidationService;
import com.example.social.server.service.RecommendationWeightExperimentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

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
        return ResponseEntity.ok(echoService.run(repeats, usersPerCommunity, postsPerUser, seed));
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
                throw new BadRequestException("candidate: п'ять чисел через кому, напр. 0.1,0.35,0.35,0.1,0.1");
            }
        }
        return ResponseEntity.ok(weightService.run(new RecommendationWeightExperimentService.Params(
                users, postsPerUser, likesPerUser, followsPerUser, hiddenShare,
                recencyTauDays, seed, k, gridSteps, includeGrid, candidateWeights)));
    }

    @DeleteMapping("/recommendation-weights/leftovers")
    public ResponseEntity<?> weightsCleanup() {
        return ResponseEntity.ok(weightService.cleanupLeftovers());
    }
}