package com.example.social.server.service;

import java.util.ArrayList;
import java.util.List;

/** Перебір вагових коефіцієнтів гібридної оцінки та оцінка якості ранжування. */
public final class WeightSearch {

    /** Кількість компонентів: популярність, item-CF, граф, семантика, свіжість. */
    public static final int COMPONENTS = 5;

    private WeightSearch() {
    }

    /**
     * @param scores   [кандидат][компонент] — нормалізовані оцінки компонентів
     * @param followed чи підписаний користувач на автора кандидата
     * @param relevant чи є кандидат прихованим «правильним» постом
     * @param tieBreak випадкові числа для детермінованого розв'язання нічиїх
     */
    public record UserCase(long userId, double[][] scores, boolean[] followed, boolean[] relevant,
                           int totalRelevant, double[] tieBreak) {
        public UserCase withTieBreak(double[] newTieBreak) {
            return new UserCase(userId, scores, followed, relevant, totalRelevant, newTieBreak);
        }

        public int size() {
            return scores.length;
        }
    }

    /** Усі набори невід'ємних ваг із кроком 1/steps, що в сумі дають 1. */
    public static List<double[]> enumerateGrid(int steps) {
        List<double[]> result = new ArrayList<>();
        fill(new int[COMPONENTS], 0, steps, steps, result);
        return result;
    }

    private static void fill(int[] current, int index, int remaining, int steps, List<double[]> out) {
        if (index == COMPONENTS - 1) {
            current[index] = remaining;
            double[] w = new double[COMPONENTS];
            for (int i = 0; i < COMPONENTS; i++) {
                w[i] = current[i] / (double) steps;
            }
            out.add(w);
            return;
        }
        for (int v = 0; v <= remaining; v++) {
            current[index] = v;
            fill(current, index + 1, remaining - v, steps, out);
        }
    }

    public static RankingMetrics.Metrics evaluate(UserCase uc, double[] w, double followedBonus, int k) {
        int n = uc.size();
        double[] total = new double[n];
        for (int i = 0; i < n; i++) {
            double[] s = uc.scores()[i];
            double t = 0.0;
            for (int j = 0; j < COMPONENTS; j++) {
                t += w[j] * s[j];
            }
            if (uc.followed()[i]) {
                t += followedBonus;
            }
            total[i] = t;
        }

        int relCount = 0;
        for (boolean b : uc.relevant()) {
            if (b) {
                relCount++;
            }
        }
        int[] ranks = new int[relCount];
        int r = 0;
        for (int i = 0; i < n; i++) {
            if (!uc.relevant()[i]) {
                continue;
            }
            int rank = 1;
            for (int j = 0; j < n; j++) {
                if (j != i && ahead(total, uc.tieBreak(), j, i)) {
                    rank++;
                }
            }
            ranks[r++] = rank;
        }
        return RankingMetrics.fromRanks(ranks, uc.totalRelevant(), k);
    }

    public static RankingMetrics.Metrics evaluateMean(List<UserCase> cases, double[] w, double followedBonus, int k) {
        List<RankingMetrics.Metrics> list = new ArrayList<>(cases.size());
        for (UserCase uc : cases) {
            list.add(evaluate(uc, w, followedBonus, k));
        }
        return RankingMetrics.mean(list);
    }

    /** true, якщо кандидат j у списку стоїть вище за кандидата i. */
    private static boolean ahead(double[] total, double[] tie, int j, int i) {
        if (total[j] != total[i]) {
            return total[j] > total[i];
        }
        if (tie[j] != tie[i]) {
            return tie[j] < tie[i];
        }
        return j < i;
    }
}