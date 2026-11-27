package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Пости — рядки: «a…» — своя спільнота, «x…» — інші. */
class CommunityDiversifierTest {

    private static final Predicate<String> OUTSIDE = s -> s.startsWith("x");
    private static final Function<String, String> KEY = s -> s;
    private static final List<String> MAIN = List.of("a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "a10");
    private static final List<String> OTHERS = List.of("x1", "x2", "x3", "x4", "x5");

    @Test
    void zeroShareKeepsMainOrder() {
        assertEquals(MAIN.subList(0, 5), CommunityDiversifier.interleave(MAIN, OTHERS, OUTSIDE, KEY, 5, 0));
    }

    @Test
    void quotaIsSpreadEvenly() {
        // round(0.2·j): перший пост іншої спільноти на 3-й позиції, другий — на 8-й
        List<String> feed = CommunityDiversifier.interleave(MAIN, OTHERS, OUTSIDE, KEY, 10, 0.2);
        assertEquals(List.of("a1", "a2", "x1", "a3", "a4", "a5", "a6", "x2", "a7", "a8"), feed);
    }

    @Test
    void outsidePostsAlreadyInMainCountTowardQuota() {
        List<String> main = List.of("a1", "x9", "a2", "a3", "a4");
        List<String> feed = CommunityDiversifier.interleave(main, List.of("x9", "x1"), OUTSIDE, KEY, 5, 0.2);
        assertEquals(main, feed, "x9 уже виконує квоту — нічого не вставляється");
    }

    @Test
    void noDuplicatesWhenOutsidePostIsAlsoLaterInMain() {
        List<String> main = List.of("a1", "a2", "x1", "a3");
        List<String> feed = CommunityDiversifier.interleave(main, List.of("x1", "x2"), OUTSIDE, KEY, 4, 0.5);
        assertEquals(List.of("x1", "a1", "x2", "a2"), feed);
    }

    @Test
    void fallsBackWhenOneListRunsOut() {
        assertEquals(List.of("x1", "a1", "a2", "a3"),
                CommunityDiversifier.interleave(List.of("a1", "a2", "a3"), List.of("x1"), OUTSIDE, KEY, 6, 0.5));
        assertEquals(List.of("x1", "x2"),
                CommunityDiversifier.interleave(List.of(), List.of("x1", "x2"), OUTSIDE, KEY, 5, 0.3));
    }
}