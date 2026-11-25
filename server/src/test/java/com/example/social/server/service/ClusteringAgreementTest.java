package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Еталонні значення пораховано scikit-learn 1.9 (normalized_mutual_info_score, adjusted_rand_score). */
class ClusteringAgreementTest {

    private static Map<Long, Integer> labels(int... values) {
        Map<Long, Integer> map = new HashMap<>();
        for (int i = 0; i < values.length; i++) {
            map.put((long) i, values[i]);
        }
        return map;
    }

    @Test
    void matchesScikitLearnExample() {
        Map<Long, Integer> truth = labels(0, 0, 0, 1, 1, 1);
        Map<Long, Integer> found = labels(0, 0, 1, 1, 2, 2);
        assertEquals(0.515804, ClusteringAgreement.nmi(truth, found), 1e-6);
        assertEquals(0.242424, ClusteringAgreement.ari(truth, found), 1e-6);
        // знайдені групи {0,0}, {0,1}, {1,1}: у кожній більшість — 2, 1, 2 з 6
        assertEquals(5 / 6.0, ClusteringAgreement.purity(truth, found), 1e-9);
    }

    @Test
    void renamedGroupsAreStillAPerfectMatch() {
        Map<Long, Integer> truth = labels(0, 0, 1, 1, 2, 2);
        Map<Long, Integer> found = labels(5, 5, 3, 3, 9, 9);
        assertEquals(1.0, ClusteringAgreement.nmi(truth, found), 1e-9);
        assertEquals(1.0, ClusteringAgreement.ari(truth, found), 1e-9);
    }

    @Test
    void everyoneInOneGroupCarriesNoInformation() {
        Map<Long, Integer> truth = labels(0, 0, 1, 1);
        Map<Long, Integer> found = labels(0, 0, 0, 0);
        assertEquals(0.0, ClusteringAgreement.nmi(truth, found), 1e-9);
        assertEquals(0.0, ClusteringAgreement.ari(truth, found), 1e-9);
    }

    @Test
    void splittingEachGroupInTwoIsPenalisedMoreByAri() {
        // справжні 2 групи по 4; знайдено 4 групи по 2 (кожну розрізано навпіл)
        Map<Long, Integer> truth = labels(0, 0, 0, 0, 1, 1, 1, 1);
        Map<Long, Integer> found = labels(0, 0, 1, 1, 2, 2, 3, 3);
        assertEquals(0.666667, ClusteringAgreement.nmi(truth, found), 1e-6);
        assertEquals(0.363636, ClusteringAgreement.ari(truth, found), 1e-6);
    }

    @Test
    void onlyUsersPresentInBothPartitionsAreCompared() {
        Map<Long, Integer> truth = labels(0, 0, 1, 1);
        Map<Long, Long> found = new HashMap<>(Map.of(0L, 7L, 1L, 7L, 2L, 8L, 3L, 8L, 99L, 1L));
        assertEquals(1.0, ClusteringAgreement.nmi(truth, found), 1e-9);
        assertEquals(1.0, ClusteringAgreement.ari(truth, found), 1e-9);
    }

    @Test
    void randomLabelsGiveAriNearZero() {
        java.util.Random rnd = new java.util.Random(1);
        Map<Long, Integer> truth = new HashMap<>();
        Map<Long, Integer> found = new HashMap<>();
        for (long i = 0; i < 2000; i++) {
            truth.put(i, rnd.nextInt(5));
            found.put(i, rnd.nextInt(5));
        }
        assertEquals(0.0, ClusteringAgreement.ari(truth, found), 0.01);
        assertEquals(0.0, ClusteringAgreement.nmi(truth, found), 0.01);
    }
}