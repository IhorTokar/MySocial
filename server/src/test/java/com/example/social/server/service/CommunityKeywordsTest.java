package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityKeywordsTest {

    private static final List<String> TRAVEL = List.of(
            "Влітку ми поїхали в Карпати, де піднялися на гору.",
            "Подорож у Карпати була чудова, гори вражають.",
            "Наступна подорож — до моря, а Карпати зачекають.");
    private static final List<String> TECH = List.of(
            "Нова версія операційної системи отримала швидший інтерфейс.",
            "Процесор тепер споживає менше енергії.",
            "Операційні системи оновлюються дуже часто.");

    private static List<String> words(List<String> group) {
        List<String> all = new ArrayList<>(TRAVEL);
        all.addAll(TECH);
        return CommunityKeywords.top(group, CommunityKeywords.index(all), 5).stream()
                .map(CommunityKeywords.Keyword::word).toList();
    }

    @Test
    void wordsOfTheCommunityWinOverWordsOfOthers() {
        List<String> travel = words(TRAVEL);
        assertTrue(travel.contains("карпати"));
        assertTrue(travel.contains("подорож"));
        assertFalse(travel.contains("системи"));
        assertTrue(words(TECH).contains("операційної") || words(TECH).contains("операційні"));
    }

    @Test
    void inflectedFormsShareAStem() {
        // «операційної» і «операційні» — одна основа «операці»; її мають два пости з трьох
        assertEquals(CommunityKeywords.stemSet("операційної"), CommunityKeywords.stemSet("операційні"));
    }

    @Test
    void stopWordsAndShortWordsAreIgnored() {
        assertTrue(CommunityKeywords.stemSet("Ми теж дуже часто, але").stream().allMatch(s -> s.equals("часто")));
    }

    @Test
    void wordFromASinglePostIsNotAKeyword() {
        assertFalse(words(TRAVEL).contains("полонини"));
        assertFalse(words(TECH).contains("процесор"), "лише в одному пості спільноти");
    }

    @Test
    void emptyInputGivesNothing() {
        assertTrue(CommunityKeywords.top(List.of(), CommunityKeywords.index(TECH), 5).isEmpty());
    }
}
