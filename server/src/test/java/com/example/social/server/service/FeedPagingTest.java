package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Стрічка = рекомендації, а після них — хронологія; сторінки не перетинаються й не мають пропусків. */
class FeedPagingTest {

    @Test
    void pageInsideRecommendationsNeedsNoChronology() {
        assertEquals(new RecommendationService.ChronoSlice(0, 0), RecommendationService.chronoSlice(50, 20, 20));
        assertEquals(new RecommendationService.ChronoSlice(0, 0), RecommendationService.chronoSlice(40, 20, 20));
    }

    @Test
    void pageOnTheBorderTakesTheRestFromChronology() {
        // 30 рекомендацій, сторінка 20..40: 10 рекомендованих + перші 10 хронологічних
        assertEquals(new RecommendationService.ChronoSlice(0, 10), RecommendationService.chronoSlice(30, 20, 20));
    }

    @Test
    void pageAfterRecommendationsContinuesChronologyWithoutGaps() {
        // 30 рекомендацій: сторінка 40..60 — це хронологічні 10..30
        assertEquals(new RecommendationService.ChronoSlice(10, 20), RecommendationService.chronoSlice(30, 40, 20));
    }

    @Test
    void noRecommendationsAtAll() {
        assertEquals(new RecommendationService.ChronoSlice(0, 20), RecommendationService.chronoSlice(0, 0, 20));
        assertEquals(new RecommendationService.ChronoSlice(20, 20), RecommendationService.chronoSlice(0, 20, 20));
    }
}