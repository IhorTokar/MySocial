package com.example.social.server.service;

import com.example.social.server.exception.BadRequestException;
import com.example.social.server.exception.ConflictException;
import com.example.social.server.service.CommunityStructure.Edge;
import com.example.social.server.service.SeedGeneratorService.Network;
import com.example.social.server.service.SeedGeneratorService.SeedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Наскільки точно Louvain і Leiden знаходять спільноти, які заклав генератор (NMI, ARI, чистота),
 * плюс модульність знайденого розбиття і кількість спільнот, що розпадаються на незв'язні частини.
 *
 * <p>Два режими:
 * <ul>
 *   <li>{@link #current} — на основній згенерованій мережі (seed.py), за поточними спільнотами;</li>
 *   <li>{@link #experiment} — серія тимчасових мереж з різною гомофілією (часткою підписок усередині
 *       спільноти) і кількома повторами. Тимчасові користувачі мають окремий домен пошти й не пов'язані
 *       з рештою мережі, тож основні дані не змінюються; наприкінці їх видалено, а спільноти основної
 *       мережі визначено заново.</li>
 * </ul>
 */
@Service
public class CommunityQualityService {

    private static final Logger log = LoggerFactory.getLogger(CommunityQualityService.class);

    private final CommunityGraph graph;
    private final CommunityData data;
    private final SeedGeneratorService generator;
    private final TransactionTemplate tx;

    public CommunityQualityService(CommunityGraph graph, CommunityData data, SeedGeneratorService generator,
                                   TransactionTemplate tx) {
        this.graph = graph;
        this.data = data;
        this.generator = generator;
        this.tx = tx;
    }

    /** @param refresh спершу синхронізувати граф і заново визначити спільноти обома алгоритмами */
    public Map<String, Object> current(boolean refresh) {
        if (refresh) {
            graph.sync();
            graph.detect(false);
            graph.detect(true);
        }
        Map<Long, Integer> truth = data.groundTruth();
        if (truth.isEmpty()) {
            throw new ConflictException("У мережі немає згенерованих користувачів — спершу tools/report/seed.py");
        }
        List<Edge> edges = data.follows();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("users", truth.size());
        result.put("trueCommunities", new HashSet<>(truth.values()).size());
        result.put("trueModularity", round(CommunityStructure.modularity(edges, truth)));
        result.put("algorithms", List.of(
                evaluate("louvain", truth, graph.labels(false), edges),
                evaluate("leiden", truth, graph.labels(true), edges)));
        return result;
    }

    /**
     * Серія тимчасових мереж: для кожної гомофілії — {@code repeats} мереж з різним зерном.
     *
     * @return config, summary (середнє й ст. відхилення за гомофілією й алгоритмом) і rows (кожен запуск)
     */
    public Map<String, Object> experiment(int users, int communities, List<Double> homophilies, int repeats, long seed) {
        if (users < 30 || users > 1000) throw new BadRequestException("users: від 30 до 1000");
        if (communities < 2 || communities > users / 3) throw new BadRequestException("communities: від 2 до users/3");
        if (homophilies.isEmpty() || homophilies.size() > 9) throw new BadRequestException("homophilies: від 1 до 9 значень");
        if (homophilies.stream().anyMatch(h -> h < 0 || h > 1)) throw new BadRequestException("гомофілія — від 0 до 1");
        if (repeats < 1 || repeats > 10) throw new BadRequestException("repeats: від 1 до 10");

        long started = System.nanoTime();
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            tx.execute(status -> generator.cleanup(SeedGeneratorService.EXPERIMENT_DOMAIN));   // залишки попереднього запуску
            for (double homophily : homophilies) {
                for (int r = 0; r < repeats; r++) {
                    // лише структура: без вподобань і коментарів (Louvain і Leiden працюють за підписками)
                    SeedGeneratorService.Params p = new SeedGeneratorService.Params(users, communities, 30, 1, 12, 0,
                            homophily, 0.5, 0.1, 0, 0, 0, seed + r);
                    Network net = tx.execute(status ->
                            generator.buildNetwork(p, LocalDateTime.now(), SeedGeneratorService.EXPERIMENT_DOMAIN));
                    Map<Long, Integer> truth = new HashMap<>();
                    List<Edge> edges = new ArrayList<>();
                    for (SeedUser u : net.users) {
                        truth.put(u.id, u.community);
                        u.following.forEach(to -> edges.add(new Edge(u.id, to)));
                    }
                    double trueModularity = round(CommunityStructure.modularity(edges, truth));

                    graph.sync();
                    for (boolean leiden : new boolean[]{false, true}) {
                        graph.detect(leiden);
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("homophily", homophily);
                        row.put("repeat", r);
                        row.putAll(evaluate(leiden ? "leiden" : "louvain", truth, graph.labels(leiden), edges));
                        row.put("trueModularity", trueModularity);
                        rows.add(row);
                    }
                    tx.execute(status -> generator.cleanup(SeedGeneratorService.EXPERIMENT_DOMAIN));
                    log.info("Якість спільнот: гомофілія {}, повтор {} — {}", homophily, r + 1,
                            rows.subList(rows.size() - 2, rows.size()));
                }
            }
        } finally {
            tx.execute(status -> generator.cleanup(SeedGeneratorService.EXPERIMENT_DOMAIN));
            graph.sync();
            graph.detect(false);
            graph.detect(true);
        }

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("users", users);
        config.put("communities", communities);
        config.put("homophilies", homophilies);
        config.put("repeats", repeats);
        config.put("seed", seed);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("config", config);
        result.put("summary", summarize(rows, homophilies));
        result.put("rows", rows);
        result.put("durationSeconds", round((System.nanoTime() - started) / 1e9));
        return result;
    }

    /** Порівняння знайдених спільнот зі справжніми — лише для користувачів, які є в обох. */
    static Map<String, Object> evaluate(String algorithm, Map<Long, Integer> truth, Map<Long, Long> labels,
                                        List<Edge> edges) {
        Map<Long, Long> found = new HashMap<>();
        truth.keySet().forEach(user -> {
            Long label = labels.get(user);
            if (label != null) {
                found.put(user, label);
            }
        });
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("algorithm", algorithm);
        row.put("usersCompared", found.size());
        if (found.isEmpty()) {
            row.put("error", "спільноти ще не визначені");
            return row;
        }
        row.put("nmi", round(ClusteringAgreement.nmi(truth, found)));
        row.put("ari", round(ClusteringAgreement.ari(truth, found)));
        row.put("purity", round(ClusteringAgreement.purity(truth, found)));
        row.put("foundCommunities", new HashSet<>(found.values()).size());
        row.put("modularity", round(CommunityStructure.modularity(edges, found)));
        row.put("disconnectedCommunities", CommunityStructure.disconnected(edges, found));
        return row;
    }

    private static List<Map<String, Object>> summarize(List<Map<String, Object>> rows, List<Double> homophilies) {
        List<Map<String, Object>> summary = new ArrayList<>();
        for (double homophily : homophilies) {
            for (String algorithm : List.of("louvain", "leiden")) {
                List<Map<String, Object>> group = rows.stream()
                        .filter(r -> r.get("homophily").equals(homophily) && algorithm.equals(r.get("algorithm"))
                                && !r.containsKey("error"))
                        .toList();
                if (group.isEmpty()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("homophily", homophily);
                row.put("algorithm", algorithm);
                row.put("runs", group.size());
                for (String key : List.of("nmi", "ari", "purity", "modularity", "trueModularity",
                        "foundCommunities", "disconnectedCommunities")) {
                    double[] values = group.stream().mapToDouble(r -> ((Number) r.get(key)).doubleValue()).toArray();
                    double mean = java.util.Arrays.stream(values).average().orElse(0);
                    double sd = Math.sqrt(java.util.Arrays.stream(values).map(v -> (v - mean) * (v - mean)).sum()
                            / Math.max(1, values.length - 1));
                    row.put(key + "Mean", round(mean));
                    row.put(key + "Sd", round(sd));
                }
                summary.add(row);
            }
        }
        return summary;
    }

    private static double round(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}