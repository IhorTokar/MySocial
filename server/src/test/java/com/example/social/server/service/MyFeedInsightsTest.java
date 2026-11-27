package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyFeedInsightsTest {

    @Test
    void myTagsAreComparedWithTheNetwork() {
        List<MyFeedInsights.TagShare> t = MyFeedInsights.compareTags(
                Map.of("спорт", 6L, "кіно", 2L), Map.of("спорт", 10L, "кіно", 30L, "їжа", 60L), 5);
        assertEquals("спорт", t.get(0).tag());
        assertEquals(0.75, t.get(0).mine(), 1e-9);
        assertEquals(0.1, t.get(0).network(), 1e-9);
        assertEquals(0.3, t.get(1).network(), 1e-9);
        assertTrue(MyFeedInsights.compareTags(Map.of(), Map.of("a", 1L), 5).isEmpty());
    }

    @Test
    void sentimentUsesTheNeutralBand() {
        Map<String, Double> s = MyFeedInsights.sentimentShares(Arrays.asList(0.5, 0.05, -0.05, -0.4, null));
        assertEquals(0.25, s.get("positive"), 1e-9);
        assertEquals(0.5, s.get("neutral"), 1e-9);
        assertEquals(0.25, s.get("negative"), 1e-9);
        assertEquals(4.0, s.get("posts"), 1e-9);
    }

    @Test
    void hintsSpeakPlainly() {
        List<String> bubble = MyFeedInsights.hints(0.95, 0.12, 0.0, 0.7, "спорт", 0.1, 30, 0);
        assertTrue(bubble.get(0).contains("95%") && bubble.get(0).contains("12%"));
        assertTrue(bubble.get(0).contains("може ввімкнути"));
        assertTrue(bubble.get(1).contains("#спорт"));
        assertTrue(MyFeedInsights.hints(null, null, 0, null, null, null, 0, 0).get(0).contains("Оберіть теми"));
        assertTrue(MyFeedInsights.hints(0.3, 0.12, 0.2, null, null, null, 5, 2).get(0).contains("різноманітна"));
        // маленька спільнота: 20% — у сто разів більше, ніж навмання (0.2%)
        String small = MyFeedInsights.hints(0.2, 0.002, 0.0, null, null, null, 5, 0).get(0);
        assertTrue(small.contains("тяжіє") && small.contains("0.2%"), small);
    }
}
