package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmbeddingTextTest {

    @Test
    void titleComesFirst() {
        assertEquals("Борщ за бабусиним рецептом. Буряк запікаю окремо.",
                EmbeddingText.of(" Борщ за бабусиним рецептом ", "Буряк запікаю окремо."));
        assertEquals("Що нового? Сьогодні тихо.", EmbeddingText.of("Що нового?", "Сьогодні тихо."));
    }

    @Test
    void missingPartsAreFine() {
        assertEquals("Лише текст", EmbeddingText.of(null, "Лише текст"));
        assertEquals("Лише текст", EmbeddingText.of("  ", "Лише текст"));
        assertEquals("Заголовок", EmbeddingText.of("Заголовок", null));
    }
}