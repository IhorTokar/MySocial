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

    /** Номери кандидатів за спаданням гібридної оцінки (той самий порядок, що й в evaluate). */
    public static List<Integer> order(UserCase uc, double[] w, double followedBonus) {
        double[] total = totals(uc, w, followedBonus);
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < uc.size(); i++) {
            idx.add(i);
        }
        idx.sort((a, b) -> ahead(total, uc.tieBreak(), a, b) ? -1 : ahead(total, uc.tieBreak(), b, a) ? 1 : 0);
        return idx;
    }

    /**
     * Якість стрічки після розбавлення постами інших спільнот ({@link CommunityDiversifier}):
     * ті самі метрики на прихованих вподобаннях і частка своєї спільноти серед перших k.
     */
    public record Diluted(RankingMetrics.Metrics metrics, double ownShareAtK) {
    }

    /**
     * @param outside кандидат від автора з іншої спільноти
     * @param share   гарантована частка постів інших спільнот (0 — без розбавлення)
     */
    public static Diluted evaluateDiluted(UserCase uc, double[] w, double followedBonus, int k,
                                          boolean[] outside, double share) {
        List<Integer> main = order(uc, w, followedBonus);
        List<Integer> others = main.stream().filter(i -> outside[i]).toList();
        List<Integer> feed = CommunityDiversifier.interleave(main, others, i -> outside[i], i -> i, main.size(), share);
        List<Integer> ranks = new ArrayList<>();
        int own = 0;
        for (int pos = 0; pos < feed.size(); pos++) {
            int i = feed.get(pos);
            if (uc.relevant()[i]) {
                ranks.add(pos + 1);
            }
            if (pos < k && !outside[i]) {
                own++;
            }
        }
        int[] r = ranks.stream().mapToInt(Integer::intValue).toArray();
        int shown = Math.min(k, feed.size());
        return new Diluted(RankingMetrics.fromRanks(r, uc.totalRelevant(), k), shown == 0 ? 0 : own / (double) shown);
    }

    private static double[] totals(UserCase uc, double[] w, double followedBonus) {
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
        return total;
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