package com.example.social.server.service;

import com.example.social.server.service.CommunityStructure.Edge;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Дві трійки (1-2-3 з взаємною підпискою 1↔2 і 4-5-6), міст 3 → 4, ізольований 7.
 * Еталонні модульність, провідність і PageRank пораховано networkx 3.7.
 */
class CommunityStructureTest {

    private static final List<Edge> EDGES = List.of(
            new Edge(1, 2), new Edge(2, 1), new Edge(2, 3), new Edge(3, 1),
            new Edge(4, 5), new Edge(5, 6), new Edge(6, 4), new Edge(3, 4));
    private static final Map<Long, String> LABELS =
            Map.of(1L, "a", 2L, "a", 3L, "a", 4L, "b", 5L, "b", 6L, "b");

    @Test
    void modularityMatchesNetworkx() {
        assertEquals(0.3671875, CommunityStructure.modularity(EDGES, LABELS), 1e-9);
    }

    @Test
    void everyoneTogetherHasZeroModularity() {
        Map<Long, String> one = Map.of(1L, "x", 2L, "x", 3L, "x", 4L, "x", 5L, "x", 6L, "x");
        assertEquals(0.0, CommunityStructure.modularity(EDGES, one), 1e-9);
    }

    @Test
    void densityAndConductancePerCommunity() {
        List<CommunityStructure.Stats> stats = CommunityStructure.stats(EDGES, LABELS);
        CommunityStructure.Stats a = stats.stream().filter(s -> s.community().equals("a")).findFirst().orElseThrow();
        CommunityStructure.Stats b = stats.stream().filter(s -> s.community().equals("b")).findFirst().orElseThrow();

        assertEquals(4, a.internalEdges());
        assertEquals(1, a.cut());
        assertEquals(4 / 6.0, a.density(), 1e-9);
        assertEquals(3 / 6.0, b.density(), 1e-9);
        // networkx.conductance: 1 / min(9, 7)
        assertEquals(1 / 7.0, a.conductance(), 1e-9);
        assertEquals(1 / 7.0, b.conductance(), 1e-9);
    }

    @Test
    void pageRankMatchesNetworkx() {
        Map<Long, Double> rank = CommunityStructure.pageRank(EDGES, List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L));
        assertEquals(0.102072, rank.get(1L), 1e-6);
        assertEquals(0.111152, rank.get(2L), 1e-6);
        assertEquals(0.071630, rank.get(3L), 1e-6);
        assertEquals(0.241494, rank.get(4L), 1e-6);
        assertEquals(0.024390, rank.get(7L), 1e-6);
        assertEquals(1.0, rank.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    @Test
    void bridgeEndpointsHaveHigherParticipation() {
        Map<Long, double[]> p = CommunityStructure.participation(EDGES, LABELS);
        // 3: два зв'язки в «a» і один у «b» → 1 − (4/9 + 1/9)
        assertEquals(4 / 9.0, p.get(3L)[0], 1e-9);
        assertEquals(4 / 9.0, p.get(4L)[0], 1e-9);
        assertEquals(0.0, p.get(1L)[0], 1e-9);
        assertEquals(3.0, p.get(3L)[1], 1e-9);
    }

    @Test
    void communitySplitIntoPiecesIsCountedAsDisconnected() {
        assertEquals(0, CommunityStructure.disconnected(EDGES, LABELS));
        Map<Long, String> broken = Map.of(1L, "a", 2L, "a", 5L, "a", 3L, "b", 4L, "b", 6L, "b");
        // «a» = {1, 2, 5}: 5 не пов'язаний з 1 і 2 зв'язками всередині «a»; «b» = {3, 4, 6} зв'язна через 4
        assertEquals(1, CommunityStructure.disconnected(EDGES, broken));
    }

    @Test
    void matrixCountsFollowsBetweenCommunities() {
        Map<Object, Map<Object, Integer>> m = CommunityStructure.matrix(EDGES, LABELS);
        assertEquals(4, m.get("a").get("a"));
        assertEquals(1, m.get("a").get("b"));
        assertEquals(3, m.get("b").get("b"));
        assertEquals(null, m.get("b").get("a"));
    }
}