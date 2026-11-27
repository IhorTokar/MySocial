package com.example.social.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * «Чому мені це показано»: головна причина рекомендації поста і внесок кожної ознаки в його оцінку.
 * Внесок ознаки — вага × нормалізована оцінка; головна причина — ознака з найбільшим внеском.
 * Пости, які стрічка додала з інших спільнот ({@link CommunityDiversifier}), мають власну причину.
 */
public final class FeedReasons {

    /** Порядок як у {@link RecommendationService.CandidateScores}: популярність, схожі вподобання, граф, текст, свіжість. */
    static final String[] NAMES = {"популярність", "схожі вподобання", "близькість у графі", "схожість тексту", "свіжість"};

    public static final String FOLLOWED = "Ви підписані на автора";
    public static final String POPULAR = "Популярне за останній тиждень";
    public static final String ITEM_CF = "Сподобалося людям зі схожими вподобаннями";
    public static final String GRAPH = "Сподобалося людям, близьким до вас у мережі підписок";
    public static final String SEMANTIC = "Схоже за змістом на пости, які вам сподобались";
    public static final String SEMANTIC_INTERESTS = "Схоже на обрані вами теми";
    public static final String RECENT = "Свіжий пост";
    public static final String OTHER_COMMUNITY = "З іншої спільноти — щоб стрічка не була однобокою";

    private FeedReasons() {
    }

    public record Explanation(String reason, String details) {
    }

    /**
     * @param contributions  внески п'яти ознак у порядку {@link #NAMES}
     * @param followedBonus  бонус за підписку (0, якщо автор не в підписках)
     * @param otherCommunity пост потрапив у стрічку як пост іншої спільноти
     * @param fromInterests  схожість тексту рахувалась відносно обраних тем, а не власних вподобань
     */
    public static Explanation explain(double[] contributions, double followedBonus,
                                      boolean otherCommunity, boolean fromInterests) {
        String reason;
        if (otherCommunity) {
            reason = OTHER_COMMUNITY;
        } else {
            int best = -1;
            double bestValue = followedBonus;
            for (int i = 0; i < contributions.length; i++) {
                if (contributions[i] > bestValue + 1e-12) {
                    best = i;
                    bestValue = contributions[i];
                }
            }
            reason = best < 0
                    ? (followedBonus > 0 ? FOLLOWED : RECENT)
                    : switch (best) {
                        case 0 -> POPULAR;
                        case 1 -> ITEM_CF;
                        case 2 -> GRAPH;
                        case 3 -> fromInterests ? SEMANTIC_INTERESTS : SEMANTIC;
                        default -> RECENT;
                    };
        }
        return new Explanation(reason, details(contributions, followedBonus));
    }

    /** «схожі вподобання 0.31 · близькість у графі 0.22 · підписка 0.30 = 0.83» — від більшого внеску. */
    static String details(double[] contributions, double followedBonus) {
        List<double[]> parts = new ArrayList<>();   // {індекс, внесок}; індекс -1 — підписка
        for (int i = 0; i < contributions.length; i++) {
            if (contributions[i] >= 0.005) {
                parts.add(new double[]{i, contributions[i]});
            }
        }
        if (followedBonus > 0) {
            parts.add(new double[]{-1, followedBonus});
        }
        parts.sort((a, b) -> Double.compare(b[1], a[1]));
        double total = 0;
        List<String> words = new ArrayList<>();
        for (double[] p : parts) {
            total += p[1];
            String name = p[0] < 0 ? "підписка" : NAMES[(int) p[0]];
            words.add(name + " " + String.format(Locale.ROOT, "%.2f", p[1]));
        }
        if (words.isEmpty()) {
            return "Внесок ознак: усі близькі до нуля";
        }
        return "Внесок в оцінку: " + String.join(" · ", words) + " = " + String.format(Locale.ROOT, "%.2f", total);
    }
}
