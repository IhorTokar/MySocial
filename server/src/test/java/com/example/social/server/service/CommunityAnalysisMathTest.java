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
    void samePolarityIsFullySentimentHomogeneous() {
        // різна впевненість моделі, але той самий клас — однорідність повна
        assertEquals(1.0, CommunityAnalysisService.computeSentimentHomogeneity(List.of(0.6, 0.95, 0.4, 0.99)), 1e-9);
    }

    @Test
    void threePolaritiesInEqualSharesGiveZero() {
        assertEquals(0.0, CommunityAnalysisService.computeSentimentHomogeneity(
                List.of(0.9, 0.0, -0.8, 0.7, 0.0, -0.6)), 1e-9);
    }

    @Test
    void sentimentHomogeneityMatchesHandCalculation() {
        // 3 позитивні + 1 негативний: H = -(0.75·ln 0.75 + 0.25·ln 0.25) = 0.5623, S = 1 - 0.5623 / ln 3 = 0.488
        assertEquals(0.488, CommunityAnalysisService.computeSentimentHomogeneity(List.of(0.9, 0.8, 0.7, -0.9)), 1e-3);
    }

    @Test
    void enthusiasticMajorityIsMoreHomogeneousThanEvenMix() {
        // саме цей випадок ламав стару формулу «1 − розкид»: більшість у захваті + кілька критиків
        double evenMix = CommunityAnalysisService.computeSentimentHomogeneity(
                List.of(0.9, 0.9, 0.0, 0.0, -0.9, -0.9));
        double majority = CommunityAnalysisService.computeSentimentHomogeneity(
                List.of(0.9, 0.9, 0.0, 0.0, -0.9, -0.9, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95, 0.95));
        assertTrue(majority > evenMix, "більшість однієї полярності має бути однорідніша за рівну суміш");
    }

    @Test
    void smallLexiconScoresCountAsNeutral() {
        assertEquals(0, CommunityAnalysisService.polarity(0.05));
        assertEquals(0, CommunityAnalysisService.polarity(-0.1));
        assertEquals(1, CommunityAnalysisService.polarity(0.34));
        assertEquals(-1, CommunityAnalysisService.polarity(-0.5));
    }

    @Test
    void noSentimentsGiveZero() {
        assertEquals(0.0, CommunityAnalysisService.computeSentimentHomogeneity(List.of()), 1e-9);
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