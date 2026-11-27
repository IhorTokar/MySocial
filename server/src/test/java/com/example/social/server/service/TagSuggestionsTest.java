package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagSuggestionsTest {

    private static TagSuggestions.Neighbour n(double similarity, String... tags) {
        return new TagSuggestions.Neighbour(similarity, List.of(tags));
    }

    @Test
    void tagsVoteWithTheirSimilarity() {
        // «подорожі»: 0.8 + 0.7 = 1.5 з 1.9; «гори»: 0.7 з 1.9; «їжа»: 0.4 з 1.9
        List<TagSuggestions.Suggestion> s = TagSuggestions.rank(
                List.of(n(0.8, "подорожі"), n(0.7, "подорожі", "гори"), n(0.4, "їжа")), 5, 0.15, List.of());
        assertEquals(List.of("подорожі", "гори", "їжа"), s.stream().map(TagSuggestions.Suggestion::tag).toList());
        assertEquals(1.5 / 1.9, s.get(0).share(), 1e-3);
        assertEquals(2, s.get(0).posts());
    }

    @Test
    void weakTagsAndAlreadyChosenOnesAreSkipped() {
        List<TagSuggestions.Suggestion> s = TagSuggestions.rank(
                List.of(n(0.9, "спорт"), n(0.9, "спорт"), n(0.1, "кіно")), 5, 0.15, List.of("Спорт"));
        assertTrue(s.isEmpty(), "спорт уже вказано, кіно набрало лише 5%");
    }

    @Test
    void noNeighboursNoSuggestions() {
        assertTrue(TagSuggestions.rank(List.of(), 5, 0.1, List.of()).isEmpty());
        assertTrue(TagSuggestions.rank(List.of(n(0.5)), 5, 0.1, List.of()).isEmpty());
    }

    @Test
    void limitIsRespected() {
        assertEquals(2, TagSuggestions.rank(List.of(n(0.5, "a"), n(0.5, "b"), n(0.5, "c")), 2, 0.1, List.of()).size());
    }
}
