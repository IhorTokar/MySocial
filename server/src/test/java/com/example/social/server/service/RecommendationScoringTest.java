package com.example.social.server.service;

import com.example.social.server.service.RecommendationService.CandidateScores;
import com.example.social.server.service.RecommendationService.Weights;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Формула гібридної оцінки (3.1) і свіжості — на числах з рис. 3.6 звіту. */
class RecommendationScoringTest {

    private static final Weights REPORT_WEIGHTS = new Weights(0.10, 0.35, 0.35, 0.10, 0.10, 0.3);

    /** Пост #89 з рис. 3.6: компоненти й totalScore, які повернув /api/posts/feed/debug. */
    private static CandidateScores post89(boolean followed) {
        return new CandidateScores(null, 89, 12, 0,
                0.0, 1.0, 0.7142558941105922, 0.9078596353020758, 0.031168831168831172, 746, followed);
    }

    @Test
    void totalScoreReproducesFigure36() {
        assertEquals(0.6938924095857979, post89(false).total(REPORT_WEIGHTS), 1e-15);
    }

    @Test
    void followedBonusIsAddedOnTopOfWeightedSum() {
        double withoutBonus = post89(false).total(REPORT_WEIGHTS);
        assertEquals(withoutBonus + 0.3, post89(true).total(REPORT_WEIGHTS), 1e-12);
    }

    @Test
    void zeroWeightsGiveZeroScore() {
        Weights zero = new Weights(0, 0, 0, 0, 0, 0);
        assertEquals(0.0, post89(true).total(zero), 1e-12);
    }

    @Test
    void recencyHalvesAfterOneDay() {
        assertEquals(1.0, RecommendationService.recencyScore(0), 1e-12);
        assertEquals(0.5, RecommendationService.recencyScore(24), 1e-12);
        assertEquals(0.125, RecommendationService.recencyScore(24 * 7), 1e-12);
    }

    @Test
    void recencyMatchesFigure36() {
        assertEquals(0.031168831168831172, RecommendationService.recencyScore(746), 1e-15);
    }

    @Test
    void defaultWeightsSumToOne() {
        Weights w = Weights.DEFAULT;
        assertEquals(1.0, w.popularity() + w.itemCf() + w.graphSim() + w.semantic() + w.recency(), 1e-9);
    }
}