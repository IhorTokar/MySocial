package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingMetricsTest {

    @Test
    void workedExampleMatchesHandCalculation() {
        // приховано 5, у топ-10 на позиціях 1 і 4
        RankingMetrics.Metrics m = RankingMetrics.fromRanks(new int[]{1, 4}, 5, 10);
        assertEquals(0.2, m.precision(), 1e-12);
        assertEquals(0.4, m.recall(), 1e-12);
        assertEquals(1.0, m.hit(), 1e-12);
        assertEquals(1.0, m.mrr(), 1e-12);
        assertEquals(0.4852285551, m.ndcg(), 1e-9);
    }

    @Test
    void perfectAndEmptyRankings() {
        assertEquals(1.0, RankingMetrics.fromRanks(new int[]{1, 2, 3}, 3, 10).ndcg(), 1e-12);
        assertEquals(0.0, RankingMetrics.fromRanks(new int[]{}, 3, 10).ndcg(), 1e-12);
    }

    @Test
    void itemOutsideTopKIsNotAHitButCountsForMrr() {
        RankingMetrics.Metrics m = RankingMetrics.fromRanks(new int[]{15}, 1, 10);
        assertEquals(0.0, m.hit(), 1e-12);
        assertEquals(1.0 / 15, m.mrr(), 1e-12);
    }

    @Test
    void unreachableItemsLowerRecall() {
        assertEquals(0.5, RankingMetrics.fromRanks(new int[]{1}, 2, 10).recall(), 1e-12);
    }

    @Test
    void gridSizeAndSums() {
        assertEquals(1001, WeightSearch.enumerateGrid(10).size());
        List<double[]> grid = WeightSearch.enumerateGrid(20);
        assertEquals(10626, grid.size());
        for (double[] w : grid) {
            double sum = 0;
            for (double x : w) {
                sum += x;
            }
            assertEquals(1.0, sum, 1e-9);
        }
    }

    @Test
    void weightOnInformativeComponentRanksRelevantFirst() {
        double[][] sc = {{0.1, 0, 0, 0, 0}, {0.9, 0, 0, 0, 0}, {0.3, 0, 0, 0, 0}, {0.2, 0, 0, 0, 0}};
        WeightSearch.UserCase uc = new WeightSearch.UserCase(1, sc, new boolean[4],
                new boolean[]{false, true, false, false}, 1, new double[]{.1, .2, .3, .4});
        assertEquals(1.0, WeightSearch.evaluate(uc, new double[]{1, 0, 0, 0, 0}, 0, 10).ndcg(), 1e-12);
    }

    @Test
    void ties_areBrokenByTieBreakNotByCandidateOrder() {
        double[][] sc = {{0.1, 0, 0, 0, 0}, {0.9, 0, 0, 0, 0}, {0.3, 0, 0, 0, 0}, {0.2, 0, 0, 0, 0}};
        WeightSearch.UserCase uc = new WeightSearch.UserCase(1, sc, new boolean[4],
                new boolean[]{false, true, false, false}, 1, new double[]{.1, .2, .3, .4});
        // ваги на порожній компонент: усі оцінки однакові, порядок визначає tieBreak, релевантний — другий
        RankingMetrics.Metrics m = WeightSearch.evaluate(uc, new double[]{0, 1, 0, 0, 0}, 0, 1);
        assertEquals(0.0, m.precision(), 1e-12);
        assertEquals(0.5, m.mrr(), 1e-12);
    }

    @Test
    void followedBonusCanReorderCandidates() {
        double[][] sc = {{0.1, 0, 0, 0, 0}, {0.9, 0, 0, 0, 0}};
        WeightSearch.UserCase uc = new WeightSearch.UserCase(2, sc, new boolean[]{true, false},
                new boolean[]{true, false}, 1, new double[]{.1, .2});
        assertTrue(WeightSearch.evaluate(uc, new double[]{1, 0, 0, 0, 0}, 1.0, 1).precision() > 0.99);
    }
}