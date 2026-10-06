package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityAnalysisMathTest {

    @Test
    void singleTagIsFullyHomogeneous() {
        assertEquals(1.0, CommunityAnalysisService.computeTopicHomogeneity(Map.of("a", 10L)), 1e-9);
    }

    @Test
    void noTagsGiveZero() {
        assertEquals(0.0, CommunityAnalysisService.computeTopicHomogeneity(Map.of()), 1e-9);
    }

    @Test
    void uniformTagsGiveZero() {
        Map<String, Long> uniform = Map.of("a", 5L, "b", 5L, "c", 5L, "d", 5L);
        assertEquals(0.0, CommunityAnalysisService.computeTopicHomogeneity(uniform), 1e-9);
    }

    @Test
    void skewedDistributionMatchesHandCalculation() {
        // p = 0.9 / 0.1: H = 0.469 біт, Hmax = 1 біт, T = 1 - 0.469 = 0.531
        assertEquals(0.531, CommunityAnalysisService.computeTopicHomogeneity(Map.of("a", 9L, "b", 1L)), 1e-3);
    }

    @Test
    void knownLimitation_twoEqualTagsLookAsDiverseAsTwentyEqualTags() {
        // фіксує поточну поведінку: нормалізація йде на число спостережених тегів
        Map<String, Long> two = Map.of("a", 5L, "b", 5L);
        assertEquals(0.0, CommunityAnalysisService.computeTopicHomogeneity(two), 1e-9);
    }

    @Test
    void stdDevOfConstantIsZero() {
        assertEquals(0.0, CommunityAnalysisService.computeStdDev(List.of(0.3, 0.3, 0.3), 0.3), 1e-9);
    }

    @Test
    void stdDevOfMinusOneAndOneIsOne() {
        assertEquals(1.0, CommunityAnalysisService.computeStdDev(List.of(-1.0, 1.0), 0.0), 1e-9);
    }

    @Test
    void stdDevOfSingleValueIsZero() {
        assertEquals(0.0, CommunityAnalysisService.computeStdDev(List.of(0.5), 0.5), 1e-9);
    }

    @Test
    void spearmanDetectsMonotonicRelationship() {
        assertEquals(1.0, SpearmanCorrelation.compute(new double[]{1, 2, 3, 4, 5}, new double[]{1, 4, 9, 16, 25}), 1e-9);
        assertEquals(-1.0, SpearmanCorrelation.compute(new double[]{1, 2, 3, 4, 5}, new double[]{9, 7, 5, 3, 1}), 1e-9);
    }

    @Test
    void spearmanHandlesTiesAndMatchesHandCalculation() {
        assertEquals(1.0, SpearmanCorrelation.compute(new double[]{1, 2, 2, 3}, new double[]{10, 20, 20, 30}), 1e-9);
        assertEquals(0.8, SpearmanCorrelation.compute(new double[]{1, 2, 3, 4, 5}, new double[]{2, 1, 4, 3, 5}), 1e-9);
    }

    @Test
    void spearmanOfConstantSampleIsNaN() {
        assertTrue(Double.isNaN(SpearmanCorrelation.compute(new double[]{1, 2, 3}, new double[]{5, 5, 5})));
    }
}