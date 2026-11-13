package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscoveryServiceTest {

    @Test
    void followedByTextUsesUkrainianPlurals() {
        assertEquals("Читає ваша підписка", DiscoveryService.followedByText(1));
        assertEquals("Читають 2 ваші підписки", DiscoveryService.followedByText(2));
        assertEquals("Читають 5 ваших підписок", DiscoveryService.followedByText(5));
        assertEquals("Читають 12 ваших підписок", DiscoveryService.followedByText(12));
        assertEquals("Читають 21 ваша підписка", DiscoveryService.followedByText(21));
        assertEquals("Читають 23 ваші підписки", DiscoveryService.followedByText(23));
    }
}