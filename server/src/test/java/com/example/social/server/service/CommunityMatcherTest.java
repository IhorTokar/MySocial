package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Зіставлення спільнот між знімками за складом (коефіцієнт Жаккара). */
class CommunityMatcherTest {

    @Test
    void jaccardOfIdenticalDisjointAndPartialSets() {
        assertEquals(1.0, CommunityMatcher.jaccard(List.of(1L, 2L), List.of(2L, 1L)), 1e-12);
        assertEquals(0.0, CommunityMatcher.jaccard(List.of(1L, 2L), List.of(3L, 4L)), 1e-12);
        // {1,2,3} ∩ {2,3,4} = 2, ∪ = 4
        assertEquals(0.5, CommunityMatcher.jaccard(List.of(1L, 2L, 3L), List.of(2L, 3L, 4L)), 1e-12);
    }

    @Test
    void picksCommunityWithLargestOverlap() {
        Map<Long, List<Long>> previous = Map.of(
                10L, List.of(1L, 2L, 9L),          // збіг 2/4 = 0.5
                20L, List.of(1L, 2L, 3L, 4L));     // збіг 3/4 = 0.75
        Optional<CommunityMatcher.Match> match = CommunityMatcher.bestMatch(List.of(1L, 2L, 3L), previous, 0.3);

        assertEquals(20L, match.orElseThrow().community());
        assertEquals(0.75, match.orElseThrow().jaccard(), 1e-12);
    }

    @Test
    void overlapBelowThresholdMeansNewCommunity() {
        Map<Long, List<Long>> previous = Map.of(10L, List.of(1L, 7L, 8L, 9L));   // збіг 1/6
        assertTrue(CommunityMatcher.bestMatch(List.of(1L, 2L, 3L), previous, 0.3).isEmpty());
    }

    @Test
    void tieIsResolvedBySmallerCommunityNumber() {
        Map<Long, List<Long>> previous = Map.of(
                30L, List.of(1L, 5L),
                12L, List.of(2L, 6L));
        assertEquals(12L, CommunityMatcher.bestMatch(List.of(1L, 2L), previous, 0.3).orElseThrow().community());
    }

    @Test
    void noPreviousCommunitiesGivesNoMatch() {
        assertTrue(CommunityMatcher.bestMatch(List.of(1L), Map.of(), 0.0).isEmpty());
    }
}