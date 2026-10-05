package com.example.social.server.service;

import java.util.List;

/** Офлайн-метрики якості ранжування з бінарною релевантністю. */
public final class RankingMetrics {

    private RankingMetrics() {
    }

    public record Metrics(double precision, double recall, double hit, double ndcg, double mrr) {
        public static Metrics zero() {
            return new Metrics(0, 0, 0, 0, 0);
        }
    }

    /**
     * @param ranks         позиції (з одиниці) релевантних елементів, присутніх у впорядкованому списку
     * @param totalRelevant загальна кількість релевантних елементів користувача, включно з тими,
     *                      яких у списку немає (вони знижують recall)
     * @param k             довжина верху списку
     */
    public static Metrics fromRanks(int[] ranks, int totalRelevant, int k) {
        if (totalRelevant <= 0) {
            throw new IllegalArgumentException("totalRelevant має бути додатним");
        }
        if (k <= 0) {
            throw new IllegalArgumentException("k має бути додатним");
        }
        int hits = 0;
        double dcg = 0.0;
        int best = Integer.MAX_VALUE;
        for (int rank : ranks) {
            if (rank <= k) {
                hits++;
                dcg += 1.0 / log2(rank + 1);
            }
            best = Math.min(best, rank);
        }
        double idcg = 0.0;
        for (int i = 1; i <= Math.min(totalRelevant, k); i++) {
            idcg += 1.0 / log2(i + 1);
        }
        return new Metrics(
                hits / (double) k,
                hits / (double) totalRelevant,
                hits > 0 ? 1.0 : 0.0,
                dcg / idcg,
                ranks.length > 0 ? 1.0 / best : 0.0);
    }

    public static Metrics mean(List<Metrics> items) {
        if (items.isEmpty()) {
            return Metrics.zero();
        }
        double p = 0, r = 0, h = 0, n = 0, m = 0;
        for (Metrics x : items) {
            p += x.precision();
            r += x.recall();
            h += x.hit();
            n += x.ndcg();
            m += x.mrr();
        }
        int size = items.size();
        return new Metrics(p / size, r / size, h / size, n / size, m / size);
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}