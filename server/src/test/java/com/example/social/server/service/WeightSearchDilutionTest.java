package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeightSearchDilutionTest {

    /** 6 кандидатів з однією ознакою: оцінки 0.9…0.4; прихований (релевантний) — кандидат 4 з іншої спільноти. */
    private static WeightSearch.UserCase userCase() {
        double[][] scores = new double[6][WeightSearch.COMPONENTS];
        for (int i = 0; i < 6; i++) {
            scores[i][0] = 0.9 - 0.1 * i;
        }
        boolean[] relevant = {false, false, false, false, true, false};
        return new WeightSearch.UserCase(1, scores, new boolean[6], relevant, 1, new double[6]);
    }

    private static final double[] W = {1, 0, 0, 0, 0};
    private static final boolean[] OUTSIDE = {false, false, false, false, true, true};

    @Test
    void orderFollowsScores() {
        assertEquals(List.of(0, 1, 2, 3, 4, 5), WeightSearch.order(userCase(), W, 0));
    }

    @Test
    void zeroShareEqualsPlainEvaluation() {
        WeightSearch.Diluted d = WeightSearch.evaluateDiluted(userCase(), W, 0, 3, OUTSIDE, 0);
        assertEquals(WeightSearch.evaluate(userCase(), W, 0, 3), d.metrics());
        assertEquals(1.0, d.ownShareAtK(), 1e-12);
    }

    @Test
    void dilutionLiftsAnOutsidePostIntoTheTop() {
        // частка 0.5: інший пост уже на 1-й позиції (round(0.5·1) = 1) — кандидат 4 стає першим
        WeightSearch.Diluted d = WeightSearch.evaluateDiluted(userCase(), W, 0, 3, OUTSIDE, 0.5);
        assertEquals(1.0, d.metrics().ndcg(), 1e-12);
        assertEquals(1.0 / 3, d.ownShareAtK(), 1e-12);   // 4, 0, 5 → своя лише 0
    }
}
