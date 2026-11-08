package com.example.social.client.graph;

import java.util.Random;

/**
 * Силова розкладка графа (Fruchterman–Reingold) з урахуванням спільнот.
 *
 * Сили: усі вузли відштовхуються (k²/d), ребра притягують кінці (d²/k),
 * кожен вузол слабко тягнеться до центру своєї спільноти, а весь граф — до центру полотна.
 * Початкові позиції: центри спільнот на колі, учасники — випадково навколо свого центру.
 * Без JavaFX — чиста математика, рахується у фоновому потоці.
 */
public final class ForceLayout {

    /** Розмір «світу», у якому рахуються координати; на екрані масштабуються під полотно. */
    public static final double SIZE = 1000.0;

    /** Тяжіння до центру своєї спільноти: робить кластери компактними й відокремленими. */
    private static final double COMMUNITY_PULL = 0.6;
    /** Тяжіння до центру полотна: не дає слабо зв'язаним вузлам відлетіти далеко. */
    private static final double GRAVITY = 0.25;
    /** Послаблення пружин ребер, щоб великі спільноти не стискалися в точку. */
    private static final double EDGE_STRENGTH = 0.35;

    private ForceLayout() {
    }

    /**
     * @param nodeCount  кількість вузлів (індекси 0..n-1)
     * @param edges      ребра як пари індексів [from, to]; напрям для розкладки не важливий
     * @param groups     номер групи (спільноти) кожного вузла, -1 — без спільноти
     * @param iterations кількість ітерацій (300–500 достатньо для сотень вузлів)
     * @param seed       зерно випадковості: однакове зерно — однакова картинка
     * @return координати [x, y] кожного вузла в межах 0..SIZE
     */
    public static double[][] compute(int nodeCount, int[][] edges, int[] groups, int iterations, long seed) {
        double[][] pos = new double[nodeCount][2];
        if (nodeCount == 0) {
            return pos;
        }
        Random random = new Random(seed);
        double center = SIZE / 2;

        // Центри груп рівномірно на колі
        int groupCount = 0;
        for (int g : groups) {
            groupCount = Math.max(groupCount, g + 1);
        }
        double ring = SIZE * 0.3;
        for (int i = 0; i < nodeCount; i++) {
            double cx = center;
            double cy = center;
            if (groups[i] >= 0 && groupCount > 1) {
                double angle = 2 * Math.PI * groups[i] / groupCount;
                cx += ring * Math.cos(angle);
                cy += ring * Math.sin(angle);
            }
            pos[i][0] = cx + (random.nextDouble() - 0.5) * SIZE * 0.2;
            pos[i][1] = cy + (random.nextDouble() - 0.5) * SIZE * 0.2;
        }

        double k = Math.sqrt(SIZE * SIZE / nodeCount) * 0.75;   // «ідеальна» відстань між вузлами
        double temperature = SIZE / 10;                          // максимальний крок, поступово зменшується
        double[][] disp = new double[nodeCount][2];
        double[][] groupCenter = new double[Math.max(groupCount, 1)][2];
        int[] groupSize = new int[Math.max(groupCount, 1)];

        for (int iter = 0; iter < iterations; iter++) {
            for (double[] d : disp) {
                d[0] = 0;
                d[1] = 0;
            }

            // Відштовхування між усіма парами
            for (int i = 0; i < nodeCount; i++) {
                for (int j = i + 1; j < nodeCount; j++) {
                    double dx = pos[i][0] - pos[j][0];
                    double dy = pos[i][1] - pos[j][1];
                    double dist = Math.max(Math.hypot(dx, dy), 0.01);
                    double force = k * k / dist;
                    double fx = dx / dist * force;
                    double fy = dy / dist * force;
                    disp[i][0] += fx;
                    disp[i][1] += fy;
                    disp[j][0] -= fx;
                    disp[j][1] -= fy;
                }
            }

            // Притягування вздовж ребер
            for (int[] e : edges) {
                int a = e[0];
                int b = e[1];
                double dx = pos[a][0] - pos[b][0];
                double dy = pos[a][1] - pos[b][1];
                double dist = Math.max(Math.hypot(dx, dy), 0.01);
                double force = dist * dist / k * EDGE_STRENGTH;
                double fx = dx / dist * force;
                double fy = dy / dist * force;
                disp[a][0] -= fx;
                disp[a][1] -= fy;
                disp[b][0] += fx;
                disp[b][1] += fy;
            }

            // Центри спільнот на цій ітерації
            java.util.Arrays.fill(groupSize, 0);
            for (double[] c : groupCenter) {
                c[0] = 0;
                c[1] = 0;
            }
            for (int i = 0; i < nodeCount; i++) {
                if (groups[i] >= 0) {
                    groupCenter[groups[i]][0] += pos[i][0];
                    groupCenter[groups[i]][1] += pos[i][1];
                    groupSize[groups[i]]++;
                }
            }

            // Тяжіння до центру своєї спільноти і до центру полотна
            for (int i = 0; i < nodeCount; i++) {
                int g = groups[i];
                if (g >= 0 && groupSize[g] > 1) {
                    double gx = groupCenter[g][0] / groupSize[g];
                    double gy = groupCenter[g][1] / groupSize[g];
                    disp[i][0] += (gx - pos[i][0]) * COMMUNITY_PULL;
                    disp[i][1] += (gy - pos[i][1]) * COMMUNITY_PULL;
                }
                disp[i][0] += (center - pos[i][0]) * GRAVITY;
                disp[i][1] += (center - pos[i][1]) * GRAVITY;
            }

            // Зсув, обмежений «температурою»
            for (int i = 0; i < nodeCount; i++) {
                double len = Math.max(Math.hypot(disp[i][0], disp[i][1]), 0.01);
                double step = Math.min(len, temperature);
                pos[i][0] += disp[i][0] / len * step;
                pos[i][1] += disp[i][1] / len * step;
            }
            temperature = Math.max(temperature * 0.97, 0.5);
        }

        fitToSize(pos);
        return pos;
    }

    /** Масштабує координати так, щоб граф займав 0..SIZE з невеликим відступом. */
    private static void fitToSize(double[][] pos) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] p : pos) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        double span = Math.max(Math.max(maxX - minX, maxY - minY), 1e-6);
        double margin = SIZE * 0.05;
        double scale = (SIZE - 2 * margin) / span;
        double offsetX = (SIZE - (maxX - minX) * scale) / 2;
        double offsetY = (SIZE - (maxY - minY) * scale) / 2;
        for (double[] p : pos) {
            p[0] = (p[0] - minX) * scale + offsetX;
            p[1] = (p[1] - minY) * scale + offsetY;
        }
    }
}