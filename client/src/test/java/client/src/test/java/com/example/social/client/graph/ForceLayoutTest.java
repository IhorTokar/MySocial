package com.example.social.client.graph;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Силова розкладка: відтворюваність, межі полотна і розділення спільнот на штучному графі. */
class ForceLayoutTest {

    private static final int GROUP_SIZE = 20;

    /** Дві щільні групи по 20 вузлів і лише 3 ребра між ними — як дві ехо-спільноти. */
    private static Graph twoCommunities() {
        int n = 2 * GROUP_SIZE;
        int[] groups = new int[n];
        for (int i = GROUP_SIZE; i < n; i++) {
            groups[i] = 1;
        }
        Random random = new Random(7);
        List<int[]> edges = new ArrayList<>();
        for (int g = 0; g < 2; g++) {
            int offset = g * GROUP_SIZE;
            for (int k = 0; k < 60; k++) {
                int a = offset + random.nextInt(GROUP_SIZE);
                int b = offset + random.nextInt(GROUP_SIZE);
                if (a != b) {
                    edges.add(new int[]{a, b});
                }
            }
        }
        edges.add(new int[]{0, GROUP_SIZE});
        edges.add(new int[]{1, GROUP_SIZE + 1});
        edges.add(new int[]{2, GROUP_SIZE + 2});
        return new Graph(n, edges.toArray(new int[0][]), groups);
    }

    private record Graph(int n, int[][] edges, int[] groups) {
        double[][] layout(long seed) {
            return ForceLayout.compute(n, edges, groups, 300, seed);
        }
    }

    @Test
    void sameSeedGivesSamePicture() {
        Graph g = twoCommunities();
        double[][] first = g.layout(42);
        double[][] second = g.layout(42);
        for (int i = 0; i < g.n(); i++) {
            assertArrayEquals(first[i], second[i], 1e-12);
        }
    }

    @Test
    void differentSeedGivesDifferentPicture() {
        Graph g = twoCommunities();
        assertFalse(g.layout(42)[0][0] == g.layout(43)[0][0]);
    }

    @Test
    void allNodesFitIntoCanvasSpace() {
        for (double[] p : twoCommunities().layout(42)) {
            assertTrue(p[0] >= 0 && p[0] <= ForceLayout.SIZE, "x = " + p[0]);
            assertTrue(p[1] >= 0 && p[1] <= ForceLayout.SIZE, "y = " + p[1]);
            assertFalse(Double.isNaN(p[0]) || Double.isNaN(p[1]));
        }
    }

    @Test
    void communitiesAreDrawnAsSeparateClusters() {
        Graph g = twoCommunities();
        double[][] pos = g.layout(42);

        double[] c0 = centroid(pos, 0, GROUP_SIZE);
        double[] c1 = centroid(pos, GROUP_SIZE, 2 * GROUP_SIZE);
        double between = Math.hypot(c0[0] - c1[0], c0[1] - c1[1]);
        double spread = Math.max(meanDistance(pos, c0, 0, GROUP_SIZE), meanDistance(pos, c1, GROUP_SIZE, 2 * GROUP_SIZE));

        // центри груп далі один від одного, ніж учасники від центру своєї групи
        assertTrue(between > 1.5 * spread, "between = " + between + ", spread = " + spread);
    }

    @Test
    void emptyAndSingleNodeGraphsDoNotFail() {
        assertEquals(0, ForceLayout.compute(0, new int[0][], new int[0], 100, 1).length);

        double[][] single = ForceLayout.compute(1, new int[0][], new int[]{-1}, 100, 1);
        assertFalse(Double.isNaN(single[0][0]) || Double.isNaN(single[0][1]));
    }

    private static double[] centroid(double[][] pos, int from, int to) {
        double x = 0;
        double y = 0;
        for (int i = from; i < to; i++) {
            x += pos[i][0];
            y += pos[i][1];
        }
        return new double[]{x / (to - from), y / (to - from)};
    }

    private static double meanDistance(double[][] pos, double[] center, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) {
            sum += Math.hypot(pos[i][0] - center[0], pos[i][1] - center[1]);
        }
        return sum / (to - from);
    }
}