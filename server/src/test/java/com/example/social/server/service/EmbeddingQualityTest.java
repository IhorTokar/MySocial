package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmbeddingQualityTest {

    // 6 постів: 1–3 про «спорт», 4–6 про «кіно»; пост 3 ще й про «музику»
    private static final Map<Long, List<String>> TAGS = Map.of(
            1L, List.of("спорт"), 2L, List.of("спорт"), 3L, List.of("спорт", "музика"),
            4L, List.of("кіно"), 5L, List.of("кіно"), 6L, List.of("кіно"));
    private static final Map<Long, Long> AUTHORS = Map.of(1L, 10L, 2L, 10L, 3L, 11L, 4L, 12L, 5L, 12L, 6L, 13L);

    @Test
    void precisionBaselineAndLift() {
        // пост 1: сусіди 2 (спорт), 4 (кіно) → 0.5; пост 4: сусіди 5, 6 (кіно) → 1.0
        EmbeddingQuality.Result r = EmbeddingQuality.evaluate(List.of(1L, 4L),
                Map.of(1L, List.of(2L, 4L), 4L, List.of(5L, 6L)), TAGS, AUTHORS, 2);
        assertEquals(2, r.samples());
        assertEquals(0.75, r.precision(), 1e-9);
        // випадково: для 1 — 2 з 5 інших постів зі спортом, для 4 — 2 з 5 з кіно → 0.4
        assertEquals(0.4, r.baseline(), 1e-9);
        assertEquals(1.875, r.lift(), 1e-4);
        // той самий автор: 1→2 (автор 10) і 4→5 (автор 12) — 2 з 4 сусідів
        assertEquals(0.5, r.sameAuthorShare(), 1e-9);
    }

    @Test
    void anySharedTagCounts() {
        EmbeddingQuality.Result r = EmbeddingQuality.evaluate(List.of(3L), Map.of(3L, List.of(1L)), TAGS, AUTHORS, 1);
        assertEquals(1.0, r.precision(), 1e-9);
        assertEquals(List.of("музика", "спорт"), r.byTag().stream().map(EmbeddingQuality.TagResult::tag).sorted().toList());
    }

    @Test
    void postsWithoutNeighboursAreSkipped() {
        assertEquals(0, EmbeddingQuality.evaluate(List.of(1L), Map.of(), TAGS, AUTHORS, 10).samples());
    }
}
