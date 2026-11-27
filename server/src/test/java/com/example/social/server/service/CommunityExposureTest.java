package com.example.social.server.service;

import com.example.social.server.service.CommunityExposure.Item;
import com.example.social.server.service.CommunityExposure.ListStats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Спільноти: a = {1, 2}, b = {3, 4}, c = {5}; автор 9 без спільноти. */
class CommunityExposureTest {

    private static final Map<Long, String> LABELS = Map.of(1L, "a", 2L, "a", 3L, "b", 4L, "b", 5L, "c");

    @Test
    void listStatsCountOwnCommunityFollowedAndTags() {
        List<Item> items = List.of(
                new Item(2, List.of("x")), new Item(2, List.of("y")),
                new Item(3, List.of("x")), new Item(9, List.of()));
        ListStats s = CommunityExposure.of(items, "a", LABELS, Set.of(3L), 3);

        assertEquals(4, s.size());
        assertEquals(0.5, s.ownShare(), 1e-9);
        assertEquals(0.25, s.followedShare(), 1e-9);
        assertEquals(3, s.communities());          // a, b і «без спільноти»
        assertEquals(2, s.distinctTags());
        // частки 1/2, 1/4, 1/4: H = 1.5 біта, нормалізація на log₂3
        assertEquals(1.5 / (Math.log(3) / Math.log(2)), s.entropy(), 1e-9);
    }

    @Test
    void randomShareIgnoresOwnPosts() {
        // користувач 1 (a) має 10 постів — їх не рахуємо; з решти 10 постів 3 у спільноті a
        double share = CommunityExposure.randomOwnShare(1, LABELS, Map.of(1L, 10, 2L, 3, 3L, 5, 4L, 2));
        assertEquals(0.3, share, 1e-9);
    }

    @Test
    void entropyBounds() {
        assertEquals(1.0, CommunityExposure.normalizedEntropy(List.of(2, 2, 2, 2), 4), 1e-9);
        assertEquals(0.0, CommunityExposure.normalizedEntropy(List.of(7), 4), 1e-9);
        assertEquals(0.5, CommunityExposure.normalizedEntropy(List.of(1, 1), 4), 1e-9);
    }

    @Test
    void topKKeepsOriginalOrderOnTies() {
        assertEquals(List.of(1, 0, 2), CommunityExposure.topK(new double[]{0.5, 0.9, 0.5, 0.1}, 3));
        assertEquals(List.of(0, 1), CommunityExposure.topK(new double[]{1, 1}, 5));
    }

    @Test
    void emptyListGivesZeros() {
        ListStats s = CommunityExposure.of(List.of(), "a", LABELS, Set.of(), 3);
        assertEquals(0, s.size());
        assertEquals(0.0, s.ownShare());
    }

    @Test
    void interestMatchCountsPostsWithAnyFavouriteTag() {
        List<Item> items = List.of(new Item(2, List.of("x", "z")), new Item(3, List.of("y")),
                new Item(4, List.of()), new Item(5, List.of("q")));
        assertEquals(0.5, CommunityExposure.interestMatch(items, Set.of("x", "y")), 1e-9);
        assertEquals(0.0, CommunityExposure.interestMatch(items, Set.of()), 1e-9);
    }

    @Test
    void topTagsByCountThenAlphabet() {
        assertEquals(List.of("b", "a", "c"),
                List.copyOf(CommunityExposure.topTags(Map.of("a", 2, "b", 5, "c", 2, "d", 1), 3)));
    }

    @Test
    void meanAndSampleSd() {
        double[] m = CommunityExposure.meanSd(List.of(1.0, 2.0, 3.0));
        assertEquals(2.0, m[0], 1e-9);
        assertEquals(1.0, m[1], 1e-9);
    }
}