package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserInterestServiceTest {

    @Test
    void tagsAreCleanedAndDeduplicated() {
        assertEquals(List.of("спорт", "кіно"),
                UserInterestService.normalize(Arrays.asList(" #спорт", "##кіно", "спорт", "", null, "#")));
        assertEquals(List.of(), UserInterestService.normalize(null));
    }
}
