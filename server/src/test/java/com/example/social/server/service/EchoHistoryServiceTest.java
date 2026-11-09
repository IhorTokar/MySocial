package com.example.social.server.service;

import com.example.social.shared.dto.CommunityAnalysisDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Підсумок знімка: середній echo-score має зважуватись за розміром спільноти. */
class EchoHistoryServiceTest {

    private static CommunityAnalysisDto community(long id, int members, double echoScore) {
        return community(id, members, 5, echoScore);
    }

    private static CommunityAnalysisDto community(long id, int members, int posts, double echoScore) {
        return new CommunityAnalysisDto(id, members, posts, List.of(), 0.0, null, 0.0, 0.0, echoScore);
    }

    @Test
    void weightedScoreCountsLargerCommunitiesMore() {
        EchoHistoryService.Summary summary = EchoHistoryService.summarize(List.of(
                community(1, 2, 0.8),
                community(2, 6, 0.4)));

        assertEquals(2, summary.communityCount());
        assertEquals(8, summary.userCount());
        assertEquals(0.5, summary.weightedEchoScore(), 1e-12);   // (2·0.8 + 6·0.4) / 8, а не (0.8 + 0.4) / 2
        assertEquals(0.8, summary.maxEchoScore(), 1e-12);
    }

    @Test
    void noCommunitiesGivesZeros() {
        EchoHistoryService.Summary summary = EchoHistoryService.summarize(List.of());
        assertEquals(0, summary.userCount());
        assertEquals(0.0, summary.weightedEchoScore(), 1e-12);
    }

    @Test
    void communitiesWithoutPostsDoNotDiluteAverage() {
        EchoHistoryService.Summary summary = EchoHistoryService.summarize(List.of(
                community(1, 4, 0.6),
                community(2, 10, 0, 0.0)));   // нові користувачі без постів

        assertEquals(14, summary.userCount());        // учасників рахуємо всіх
        assertEquals(0.6, summary.weightedEchoScore(), 1e-12);   // а середнє — лише по спільнотах з постами
    }
}