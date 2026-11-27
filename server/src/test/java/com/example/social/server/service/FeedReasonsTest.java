package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedReasonsTest {

    @Test
    void largestContributionIsTheReason() {
        double[] c = {0.02, 0.31, 0.22, 0.05, 0.04};
        assertEquals(FeedReasons.ITEM_CF, FeedReasons.explain(c, 0.0, false, false).reason());
        assertEquals(FeedReasons.GRAPH, FeedReasons.explain(new double[]{0, 0.1, 0.3, 0, 0}, 0, false, false).reason());
    }

    @Test
    void followBonusCanBeTheMainReason() {
        assertEquals(FeedReasons.FOLLOWED,
                FeedReasons.explain(new double[]{0.01, 0.1, 0.05, 0.02, 0.03}, 0.3, false, false).reason());
    }

    @Test
    void textSimilarityFromChosenTopicsIsNamedSo() {
        double[] c = {0, 0, 0, 0.09, 0.02};
        assertEquals(FeedReasons.SEMANTIC, FeedReasons.explain(c, 0, false, false).reason());
        assertEquals(FeedReasons.SEMANTIC_INTERESTS, FeedReasons.explain(c, 0, false, true).reason());
    }

    @Test
    void otherCommunityOverridesScores() {
        assertEquals(FeedReasons.OTHER_COMMUNITY,
                FeedReasons.explain(new double[]{0, 0.3, 0, 0, 0}, 0.3, true, false).reason());
    }

    @Test
    void detailsListContributionsFromLargestWithTotal() {
        String d = FeedReasons.explain(new double[]{0.02, 0.31, 0.22, 0, 0.04}, 0.3, false, false).details();
        assertEquals("Внесок в оцінку: схожі вподобання 0.31 · підписка 0.30 · близькість у графі 0.22 · "
                + "свіжість 0.04 · популярність 0.02 = 0.89", d);
        assertTrue(FeedReasons.explain(new double[5], 0, false, false).details().contains("нуля"));
    }
}
