package com.example.social.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Вибір джерела тональності: модель → словник, якщо NLP-сервіс недоступний → нуль для порожнього тексту. */
class SentimentServiceTest {

    private final SentimentApiService api = mock(SentimentApiService.class);
    private final SentimentService service = new SentimentService(api);

    @Test
    void usesModelWhenNlpServiceAnswers() {
        when(api.analyze("Сьогодні чудовий день")).thenReturn(0.56);

        SentimentService.Result result = service.analyzeWithSource("Сьогодні чудовий день");

        assertEquals(0.56, result.value(), 1e-12);
        assertEquals(SentimentService.SOURCE_MODEL, result.source());
    }

    @Test
    void fallsBackToLexiconWhenNlpServiceIsDown() {
        when(api.analyze(anyString())).thenReturn(null);

        SentimentService.Result positive = service.analyzeWithSource("Чудовий радісний день");
        SentimentService.Result negative = service.analyzeWithSource("Жахливо і сумно");

        assertEquals(SentimentService.SOURCE_LEXICON, positive.source());
        assertTrue(positive.value() > 0, "позитивний текст має дати > 0");
        assertTrue(negative.value() < 0, "негативний текст має дати < 0");
    }

    @Test
    void lexiconWithoutKnownWordsIsNeutral() {
        when(api.analyze(anyString())).thenReturn(null);

        assertEquals(0.0, service.analyzeWithSource("Стіл стоїть біля вікна").value(), 1e-12);
    }

    @Test
    void emptyTextIsNeutralAndDoesNotCallNlpService() {
        SentimentService.Result result = service.analyzeWithSource("   ");

        assertEquals(0.0, result.value(), 1e-12);
        assertEquals(SentimentService.SOURCE_EMPTY, result.source());
        verifyNoInteractions(api);
    }

    @Test
    void analyzeReturnsSameValueAsAnalyzeWithSource() {
        when(api.analyze("текст")).thenReturn(-0.4);

        assertEquals(-0.4, service.analyze("текст"), 1e-12);
    }
}