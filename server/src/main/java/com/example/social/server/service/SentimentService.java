package com.example.social.server.service;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SentimentService {

    // Невеликі ілюстративні лексикони — для навчального проєкту цього достатньо;
    // для продакшн-точності варто замінити на словник типу Tone Dictionary
    // чи ML-класифікатор через NLP-сервіс
    private static final Set<String> POSITIVE_WORDS = Set.of(
            "любл", "чудов", "прекрасн", "щаст", "радіс", "супер", "круто",
            "класн", "дякую", "вдяч", "натхнен", "надихає", "обожню", "гарн",
            "весел", "приємн", "задоволен", "успіх", "перемог", "любов"
    );

    private static final Set<String> NEGATIVE_WORDS = Set.of(
            "погано", "жахлив", "сумно", "розчаров", "злий", "гнів", "страх",
            "боляч", "втом", "ненавид", "проблем", "біда", "сум", "втрат",
            "розлюч", "прикро", "жаль", "поган", "критик", "скарг"
    );

    private static final Pattern WORD_SPLIT = Pattern.compile("[^а-щьюяіїєґ']+", Pattern.CASE_INSENSITIVE);

    /**
     * Груба оцінка тональності тексту в діапазоні [-1, 1]:
     * -1 — суцільно негативний, 0 — нейтральний, 1 — суцільно позитивний.
     * Основана на підрахунку коренів слів з двох невеликих лексиконів,
     * не враховує заперечення, сарказм тощо — свідоме спрощення.
     */
    public double analyze(String text) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }

        String lower = text.toLowerCase();
        String[] words = WORD_SPLIT.split(lower);

        int positiveHits = 0;
        int negativeHits = 0;

        for (String word : words) {
            if (word.isBlank()) continue;
            for (String root : POSITIVE_WORDS) {
                if (word.contains(root)) {
                    positiveHits++;
                    break;
                }
            }
            for (String root : NEGATIVE_WORDS) {
                if (word.contains(root)) {
                    negativeHits++;
                    break;
                }
            }
        }

        int totalHits = positiveHits + negativeHits;
        if (totalHits == 0) {
            return 0.0;
        }

        return (double) (positiveHits - negativeHits) / totalHits;
    }
}