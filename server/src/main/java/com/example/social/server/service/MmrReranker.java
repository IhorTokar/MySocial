package com.example.social.server.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Maximal Marginal Relevance (Carbonell & Goldstein, 1998): перестановка стрічки
 * з урахуванням не лише релевантності, а й різноманітності.
 *
 * На кожному кроці обирається пост i, що максимізує
 *     MMR(i) = λ · rel(i) − (1 − λ) · max_{j ∈ S} sim(i, j),
 * де S — уже вибрані пости. λ = 1 — звичайне ранжування за релевантністю,
 * менші λ дедалі сильніше штрафують пости, схожі на вже показані.
 *
 * Чиста математика без Spring і БД — тому легко тестується.
 */
public final class MmrReranker {

    private MmrReranker() {
    }

    /**
     * @param relevance  релевантність кандидатів у [0, 1] (більше — краще)
     * @param similarity попарна схожість кандидатів у [0, 1], симетрична матриця
     * @param k          скільки постів вибрати
     * @param lambda     баланс релевантність/різноманітність у [0, 1]
     * @return індекси вибраних кандидатів у порядку показу
     */
    public static List<Integer> rerank(double[] relevance, double[][] similarity, int k, double lambda) {
        int n = relevance.length;
        int count = Math.min(k, n);
        List<Integer> selected = new ArrayList<>(count);
        boolean[] used = new boolean[n];
        // найбільша схожість кожного кандидата на вже вибрані — оновлюється інкрементно, без повного перерахунку
        double[] maxSimToSelected = new double[n];

        for (int step = 0; step < count; step++) {
            int best = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                if (used[i]) {
                    continue;
                }
                double score = lambda * relevance[i] - (1 - lambda) * maxSimToSelected[i];
                if (score > bestScore) {   // строго більше: при рівності лишається вищий за релевантністю (менший індекс)
                    bestScore = score;
                    best = i;
                }
            }
            used[best] = true;
            selected.add(best);
            for (int i = 0; i < n; i++) {
                if (!used[i]) {
                    maxSimToSelected[i] = Math.max(maxSimToSelected[i], similarity[i][best]);
                }
            }
        }
        return selected;
    }

    /**
     * Внутрішньосписочна різноманітність (Intra-List Diversity): середнє (1 − sim) по всіх парах списку.
     * 0 — усі пости однакові за змістом, 1 — повністю різні.
     */
    public static double intraListDiversity(List<Integer> items, double[][] similarity) {
        if (items.size() < 2) {
            return 0.0;
        }
        double sum = 0;
        int pairs = 0;
        for (int a = 0; a < items.size(); a++) {
            for (int b = a + 1; b < items.size(); b++) {
                sum += 1 - similarity[items.get(a)][items.get(b)];
                pairs++;
            }
        }
        return sum / pairs;
    }
}