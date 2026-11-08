package com.example.social.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SentimentService {

    private static final Logger log = LoggerFactory.getLogger(SentimentService.class);

    // Fallback-лексикон на випадок недоступності ML-сервісу — груба, але швидка оцінка
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

    /** Джерела значення — зберігаються в posts.sentiment_source. */
    public static final String SOURCE_MODEL = "model";
    public static final String SOURCE_LEXICON = "lexicon";
    public static final String SOURCE_EMPTY = "empty";

    /** Тональність разом з тим, як її отримано. */
    public record Result(double value, String source) {
    }

    private final SentimentApiService sentimentApiService;

    public SentimentService(SentimentApiService sentimentApiService) {
        this.sentimentApiService = sentimentApiService;
    }

    /**
     * Тональність тексту в [-1, 1]. Основний шлях — ML-модель через NLP-сервіс
     * (мультимовний DistilBERT); якщо сервіс недоступний — деградує до простого
     * лексиконного підрахунку, щоб аналіз спільнот не падав повністю.
     */
    public double analyze(String text) {
        return analyzeWithSource(text).value();
    }

    /** Те саме, що analyze(), але з позначкою джерела — для збереження в БД. */
    public Result analyzeWithSource(String text) {
        if (text == null || text.isBlank()) {
            return new Result(0.0, SOURCE_EMPTY);
        }
        Double mlResult = sentimentApiService.analyze(text);
        if (mlResult != null) {
            return new Result(mlResult, SOURCE_MODEL);
        }

        log.debug("Falling back to lexicon-based sentiment for text");
        return new Result(analyzeLexicon(text), SOURCE_LEXICON);
    }

    private double analyzeLexicon(String text) {
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