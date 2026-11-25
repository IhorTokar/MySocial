package com.example.social.server.integration;

import com.example.social.server.service.CommunityGraph;
import com.example.social.server.service.CommunityQualityService;
import com.example.social.server.service.CommunityStructureService;
import com.example.social.server.service.SeedGeneratorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Аналіз спільнот без Neo4j: замість Louvain/Leiden — підставні розбиття з відомою відповіддю.
 * Перевіряється все навколо алгоритмів: справжні спільноти з БД, NMI/ARI, структура, тимчасові мережі
 * експерименту і прибирання після нього.
 */
class CommunityInsightIntegrationTest extends IntegrationTestBase {

    @MockBean
    private CommunityGraph graph;

    @Autowired
    private CommunityQualityService quality;

    @Autowired
    private CommunityStructureService structure;

    @Autowired
    private SeedGeneratorService generator;

    @Autowired
    private JdbcTemplate jdbc;

    private static SeedGeneratorService.Params params() {
        return new SeedGeneratorService.Params(60, 3, 14, 2, 8, 5, 0.9, 0.5, 0.1, 0, 0, 0, 7);
    }

    @AfterEach
    void removeGenerated() {
        generator.cleanup();
    }

    /** «Louvain» знаходить точно справжні спільноти, «Leiden» — зливає всіх в одну. */
    private void stubPerfectAndMerged() {
        when(graph.labels(false)).thenAnswer(inv -> groundTruthLabels());
        when(graph.labels(true)).thenAnswer(inv -> {
            Map<Long, Long> one = new HashMap<>();
            groundTruthLabels().keySet().forEach(u -> one.put(u, 1L));
            return one;
        });
    }

    private Map<Long, Long> groundTruthLabels() {
        Map<Long, Long> labels = new HashMap<>();
        jdbc.query("SELECT user_id, community FROM seed_ground_truth",
                rs -> {
                    labels.put(rs.getLong(1), rs.getLong(2) + 100);   // інші номери — збіг має бути однаково повним
                });
        return labels;
    }

    @Test
    @SuppressWarnings("unchecked")
    void qualityComparesFoundCommunitiesWithGeneratorTruth() {
        generator.generate(params());
        stubPerfectAndMerged();

        Map<String, Object> result = quality.current(false);

        assertEquals(60, result.get("users"));
        assertEquals(3, result.get("trueCommunities"));
        List<Map<String, Object>> algorithms = (List<Map<String, Object>>) result.get("algorithms");
        Map<String, Object> perfect = algorithms.get(0);
        Map<String, Object> merged = algorithms.get(1);
        assertEquals(1.0, perfect.get("nmi"));
        assertEquals(1.0, perfect.get("ari"));
        assertEquals(3, perfect.get("foundCommunities"));
        assertEquals(0.0, merged.get("nmi"));
        assertEquals(1, merged.get("foundCommunities"));
        assertTrue((Double) perfect.get("modularity") > 0.4, "гомофілія 0.9 — виразні спільноти");
    }

    @Test
    @SuppressWarnings("unchecked")
    void structureDescribesEachCommunityWithLeadersAndMatrix() {
        generator.generate(params());
        stubPerfectAndMerged();

        Map<String, Object> result = structure.structure(false, 10, false);

        List<Map<String, Object>> details = (List<Map<String, Object>>) result.get("details");
        assertEquals(3, details.size());
        for (Map<String, Object> c : details) {
            assertEquals(20, c.get("size"));
            assertEquals(3, ((List<?>) c.get("leaders")).size());
            assertEquals(1.0, ((Map<String, Object>) c.get("seedCommunity")).get("share"));
            assertTrue((Double) c.get("conductance") < 0.3, "більшість зв'язків — усередині");
        }
        Map<String, Object> matrix = (Map<String, Object>) result.get("matrix");
        List<List<Integer>> follows = (List<List<Integer>>) matrix.get("follows");
        for (int i = 0; i < follows.size(); i++) {
            int row = follows.get(i).stream().mapToInt(Integer::intValue).sum();
            assertTrue(follows.get(i).get(i) > row / 2, "діагональ домінує: " + follows.get(i));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void experimentUsesTemporaryNetworksAndLeavesMainNetworkIntact() {
        generator.generate(params());
        stubPerfectAndMerged();
        long before = count("@seed.nexus.local");

        Map<String, Object> result = quality.experiment(30, 3, List.of(0.6, 0.9), 2, 1);

        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertEquals(2 * 2 * 2, rows.size(), "2 гомофілії × 2 повтори × 2 алгоритми");
        assertTrue(rows.stream().filter(r -> r.get("algorithm").equals("louvain"))
                .allMatch(r -> r.get("nmi").equals(1.0)), "підставний «Louvain» відтворює справжні спільноти");
        assertEquals(4, ((List<?>) result.get("summary")).size());
        assertEquals(0, count(SeedGeneratorService.EXPERIMENT_DOMAIN), "тимчасові мережі прибрано");
        assertEquals(before, count("@seed.nexus.local"), "основну мережу не зачеплено");
    }

    private long count(String domain) {
        return jdbc.queryForObject("SELECT count(*) FROM user_private WHERE email LIKE ?", Long.class, "%" + domain);
    }
}