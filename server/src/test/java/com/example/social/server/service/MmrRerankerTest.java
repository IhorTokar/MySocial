package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MMR: межові значення λ, відсування дублікатів і метрика різноманітності списку. */
class MmrRerankerTest {

    /**
     * Три кандидати: A і A' майже однакові за змістом (sim 0.99), B — про інше (sim 0.1).
     * За релевантністю A > A' > B.
     */
    private static final double[] RELEVANCE = {1.0, 0.95, 0.8};
    private static final double[][] SIMILARITY = {
            {1.00, 0.99, 0.10},
            {0.99, 1.00, 0.10},
            {0.10, 0.10, 1.00}};

    @Test
    void lambdaOneKeepsPureRelevanceOrder() {
        assertEquals(List.of(0, 1, 2), MmrReranker.rerank(RELEVANCE, SIMILARITY, 3, 1.0));
    }

    @Test
    void nearDuplicateIsPushedDownByDiversePost() {
        // A' майже повторює A, тому після A вище стає B
        assertEquals(List.of(0, 2, 1), MmrReranker.rerank(RELEVANCE, SIMILARITY, 3, 0.5));
    }

    @Test
    void mostRelevantPostAlwaysComesFirst() {
        for (double lambda : new double[]{0.1, 0.3, 0.5, 0.7, 0.9}) {
            assertEquals(0, MmrReranker.rerank(RELEVANCE, SIMILARITY, 3, lambda).get(0), "λ = " + lambda);
        }
    }

    @Test
    void lambdaZeroPicksLeastSimilarAfterFirst() {
        assertEquals(List.of(0, 2), MmrReranker.rerank(RELEVANCE, SIMILARITY, 2, 0.0));
    }

    @Test
    void returnsAtMostAvailableCandidatesWithoutDuplicates() {
        List<Integer> order = MmrReranker.rerank(RELEVANCE, SIMILARITY, 10, 0.5);
        assertEquals(3, order.size());
        assertEquals(3, order.stream().distinct().count());
    }

    @Test
    void emptyInputGivesEmptyResult() {
        assertTrue(MmrReranker.rerank(new double[0], new double[0][0], 5, 0.7).isEmpty());
    }

    @Test
    void diversityIsHigherAfterReranking() {
        double plain = MmrReranker.intraListDiversity(MmrReranker.rerank(RELEVANCE, SIMILARITY, 2, 1.0), SIMILARITY);
        double diverse = MmrReranker.intraListDiversity(MmrReranker.rerank(RELEVANCE, SIMILARITY, 2, 0.5), SIMILARITY);
        assertEquals(0.01, plain, 1e-9);    // A + A'  → 1 − 0.99
        assertEquals(0.90, diverse, 1e-9);  // A + B   → 1 − 0.10
    }

    @Test
    void intraListDiversityOfSingleItemIsZero() {
        assertEquals(0.0, MmrReranker.intraListDiversity(List.of(0), SIMILARITY), 1e-12);
    }
}