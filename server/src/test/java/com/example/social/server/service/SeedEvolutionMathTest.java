package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SeedEvolutionMathTest {

    @Test
    void personalHomogeneityMatchesHandCalculation() {
        // одна тема — 1; усі 10 тем порівну — 0; дві теми порівну з 10: 1 − 1 / log₂10 = 0.699
        assertEquals(1.0, SeedEvolutionService.personalHomogeneity(List.of(3, 3, 3, 3), 10), 1e-9);
        assertEquals(0.0, SeedEvolutionService.personalHomogeneity(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), 10), 1e-9);
        assertEquals(0.699, SeedEvolutionService.personalHomogeneity(List.of(1, 1, 2, 2), 10), 1e-3);
    }

    @Test
    void tooFewActionsGiveNoEstimate() {
        assertNull(SeedEvolutionService.personalHomogeneity(List.of(1, 2), 10));
    }

    @Test
    void shiftMovesInterestsTowardsTargetAndKeepsThemNormalized() {
        Map<Integer, Double> interests = new HashMap<>(Map.of(0, 0.5, 1, 0.5));
        SeedEvolutionService.shiftInterests(interests, Map.of(2, 1.0), 0.4);

        assertEquals(0.3, interests.get(0), 1e-9);
        assertEquals(0.3, interests.get(1), 1e-9);
        assertEquals(0.4, interests.get(2), 1e-9);
        assertEquals(1.0, interests.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    @Test
    void repeatedShiftConvergesAndDropsTinyLeftovers() {
        Map<Integer, Double> interests = new HashMap<>(Map.of(0, 1.0));
        for (int week = 0; week < 10; week++) {
            SeedEvolutionService.shiftInterests(interests, Map.of(5, 1.0), 0.4);
        }
        // 0.6^10 ≈ 0.006 < 1% — стара тема відкинута
        assertEquals(Map.of(5, 1.0), interests);
    }
}