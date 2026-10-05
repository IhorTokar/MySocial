package com.example.social.server.service;

import java.util.Arrays;

/** Коефіцієнт рангової кореляції Спірмена (з усередненими рангами для однакових значень). */
public final class SpearmanCorrelation {

    private SpearmanCorrelation() {
    }

    /** @return ρ у діапазоні [-1; 1], або NaN, якщо одна із вибірок стала. */
    public static double compute(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Вибірки мають різну довжину");
        }
        if (x.length < 2) {
            throw new IllegalArgumentException("Потрібно щонайменше два спостереження");
        }
        return pearson(ranks(x), ranks(y));
    }

    static double[] ranks(double[] values) {
        int n = values.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(values[a], values[b]));

        double[] ranks = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && values[order[j + 1]] == values[order[i]]) {
                j++;
            }
            double averageRank = (i + j) / 2.0 + 1.0;
            for (int k = i; k <= j; k++) {
                ranks[order[k]] = averageRank;
            }
            i = j + 1;
        }
        return ranks;
    }

    static double pearson(double[] a, double[] b) {
        int n = a.length;
        double meanA = 0, meanB = 0;
        for (int i = 0; i < n; i++) {
            meanA += a[i];
            meanB += b[i];
        }
        meanA /= n;
        meanB /= n;

        double cov = 0, varA = 0, varB = 0;
        for (int i = 0; i < n; i++) {
            double da = a[i] - meanA;
            double db = b[i] - meanB;
            cov += da * db;
            varA += da * da;
            varB += db * db;
        }
        if (varA == 0 || varB == 0) {
            return Double.NaN;
        }
        return cov / Math.sqrt(varA * varB);
    }
}