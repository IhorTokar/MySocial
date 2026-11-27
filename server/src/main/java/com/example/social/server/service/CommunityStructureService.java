package com.example.social.server.service;

import com.example.social.server.exception.ConflictException;
import com.example.social.server.service.CommunityStructure.Edge;
import com.example.social.server.service.CommunityStructure.Stats;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Структура спільнот поточної мережі: розмір, щільність, провідність, теми, лідери (PageRank),
 * «мости» (коефіцієнт участі) і матриця підписок між спільнотами. Формули — {@link CommunityStructure}.
 */
@Service
public class CommunityStructureService {

    /** «Міст» має щонайменше стільки зв'язків — інакше високий коефіцієнт участі випадковий. */
    static final int MIN_BRIDGE_DEGREE = 4;
    private static final int LEADERS = 3;
    private static final int BRIDGES = 3;
    private static final int TOP_TAGS = 3;
    private static final int KEYWORDS = 5;
    private static final String OTHERS = "інші";

    private final CommunityGraph graph;
    private final CommunityData data;

    public CommunityStructureService(CommunityGraph graph, CommunityData data) {
        this.graph = graph;
        this.data = data;
    }

    /**
     * @param top     скільки найбільших спільнот описати докладно (решта — разом як «інші» в матриці)
     * @param refresh спершу синхронізувати граф і заново визначити спільноти цим алгоритмом
     */
    public Map<String, Object> structure(boolean leiden, int top, boolean refresh) {
        if (refresh) {
            graph.sync();
            graph.detect(leiden);
        }
        Map<Long, Long> labels = graph.labels(leiden);
        if (labels.isEmpty()) {
            throw new ConflictException("Спільноти ще не визначені — спершу POST /api/graph/communities");
        }
        List<Edge> edges = data.follows();
        List<Stats> stats = CommunityStructure.stats(edges, labels).stream().filter(s -> s.size() >= 2).toList();
        Map<Long, Double> pageRank = CommunityStructure.pageRank(edges, labels.keySet());
        Map<Long, double[]> participation = CommunityStructure.participation(edges, labels);
        Map<Long, Integer> truth = data.groundTruth();
        Map<Long, Map<String, Integer>> tags = data.tagsByUser();
        Map<Long, List<String>> texts = data.textsByUser();
        CommunityKeywords.Index wordIndex = CommunityKeywords.index(
                texts.values().stream().flatMap(List::stream).toList());

        Map<Long, List<Long>> members = new HashMap<>();
        labels.forEach((user, label) -> members.computeIfAbsent(label, k -> new ArrayList<>()).add(user));
        Map<Long, Integer> followers = new HashMap<>();
        edges.forEach(e -> followers.merge(e.to(), 1, Integer::sum));

        List<Stats> described = stats.subList(0, Math.min(Math.max(1, top), stats.size()));
        Set<Long> people = new HashSet<>();
        List<Map<String, Object>> communities = new ArrayList<>();
        for (Stats s : described) {
            long label = (Long) s.community();
            List<Long> group = members.get(label);

            List<Long> leaders = group.stream()
                    .sorted(Comparator.comparingDouble((Long u) -> -pageRank.getOrDefault(u, 0.0)))
                    .limit(LEADERS).toList();
            List<Long> bridges = group.stream()
                    .filter(u -> participation.containsKey(u) && participation.get(u)[1] >= MIN_BRIDGE_DEGREE)
                    .sorted(Comparator.comparingDouble((Long u) -> -participation.get(u)[0]))
                    .limit(BRIDGES).toList();
            people.addAll(leaders);
            people.addAll(bridges);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("community", label);
            row.put("size", s.size());
            row.put("internalFollows", s.internalEdges());
            row.put("externalFollows", s.cut());
            row.put("density", round(s.density()));
            row.put("conductance", round(s.conductance()));
            row.put("topTags", topTags(group, tags));
            row.put("keywords", keywords(group, texts, wordIndex));
            row.put("seedCommunity", plurality(group, truth));
            row.put("leaders", leaders);
            row.put("bridges", bridges);
            communities.add(row);
        }

        // «Мости» всієї мережі — з найвищим коефіцієнтом участі
        List<Long> networkBridges = participation.entrySet().stream()
                .filter(e -> e.getValue()[1] >= MIN_BRIDGE_DEGREE)
                .sorted(Comparator.comparingDouble((Map.Entry<Long, double[]> e) -> -e.getValue()[0]))
                .limit(10).map(Map.Entry::getKey).toList();
        people.addAll(networkBridges);
        Map<Long, String> names = data.usernames(people);

        for (Map<String, Object> row : communities) {
            row.put("leaders", ((List<?>) row.get("leaders")).stream().map(u -> person((Long) u, names,
                    Map.of("pageRank", round(pageRank.getOrDefault((Long) u, 0.0) * labels.size()),
                            "followers", followers.getOrDefault((Long) u, 0)))).toList());
            row.put("bridges", ((List<?>) row.get("bridges")).stream().map(u -> {
                double[] p = participation.get((Long) u);
                return person((Long) u, names, Map.of("participation", round(p[0]),
                        "links", (int) p[1], "communitiesReached", (int) p[2]));
            }).toList());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("algorithm", leiden ? "leiden" : "louvain");
        result.put("users", labels.size());
        result.put("communities", stats.size());
        result.put("singletons", (int) members.values().stream().filter(g -> g.size() < 2).count());
        result.put("modularity", round(CommunityStructure.modularity(edges, labels)));
        result.put("disconnectedCommunities", CommunityStructure.disconnected(edges, labels));
        result.put("weightedConductance", round(stats.stream().mapToDouble(s -> s.conductance() * s.size()).sum()
                / Math.max(1, stats.stream().mapToInt(Stats::size).sum())));
        result.put("details", communities);
        result.put("bridges", networkBridges.stream().map(u -> {
            double[] p = participation.get(u);
            return person(u, names, Map.of("community", labels.get(u), "participation", round(p[0]),
                    "links", (int) p[1], "communitiesReached", (int) p[2]));
        }).toList());
        result.put("matrix", matrix(edges, labels, described.stream().map(s -> (Long) s.community()).toList()));
        result.put("pageRankNote", "pageRank × кількість користувачів: 1 — середній рівень впливу");
        return result;
    }

    /** Матриця підписок між найбільшими спільнотами; решта — разом як «інші». */
    private static Map<String, Object> matrix(List<Edge> edges, Map<Long, Long> labels, List<Long> shown) {
        Set<Long> visible = new HashSet<>(shown);
        Map<Long, Object> grouped = new HashMap<>();
        labels.forEach((user, label) -> grouped.put(user, visible.contains(label) ? label : OTHERS));
        Map<Object, Map<Object, Integer>> counts = CommunityStructure.matrix(edges, grouped);

        List<Object> order = new ArrayList<>(shown);
        if (grouped.containsValue(OTHERS)) {
            order.add(OTHERS);
        }
        List<List<Integer>> values = new ArrayList<>();
        for (Object row : order) {
            List<Integer> line = new ArrayList<>();
            for (Object column : order) {
                line.add(counts.getOrDefault(row, Map.of()).getOrDefault(column, 0));
            }
            values.add(line);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("labels", order.stream().map(String::valueOf).toList());
        result.put("follows", values);
        return result;
    }

    private static List<String> topTags(List<Long> group, Map<Long, Map<String, Integer>> tags) {
        Map<String, Integer> total = new HashMap<>();
        group.forEach(u -> tags.getOrDefault(u, Map.of()).forEach((tag, n) -> total.merge(tag, n, Integer::sum)));
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(total.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
        return sorted.stream().limit(TOP_TAGS).map(Map.Entry::getKey).toList();
    }

    /** Ключові слова з текстів постів учасників (TF-IDF відносно всієї мережі). */
    static List<String> keywords(List<Long> group, Map<Long, List<String>> texts, CommunityKeywords.Index index) {
        List<String> groupTexts = new ArrayList<>();
        group.forEach(u -> groupTexts.addAll(texts.getOrDefault(u, List.of())));
        return CommunityKeywords.top(groupTexts, index, KEYWORDS).stream().map(CommunityKeywords.Keyword::word).toList();
    }

    /** Найчисленніша спільнота генератора серед учасників і її частка; null — згенерованих немає. */
    private static Map<String, Object> plurality(List<Long> group, Map<Long, Integer> truth) {
        Map<Integer, Integer> counts = new HashMap<>();
        group.forEach(u -> {
            Integer c = truth.get(u);
            if (c != null) {
                counts.merge(c, 1, Integer::sum);
            }
        });
        if (counts.isEmpty()) {
            return null;
        }
        Map.Entry<Integer, Integer> best = Collections.max(counts.entrySet(), Map.Entry.comparingByValue());
        int seeded = counts.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("community", best.getKey());
        row.put("share", round(best.getValue() / (double) seeded));
        return row;
    }

    private static Map<String, Object> person(long userId, Map<Long, String> names, Map<String, Object> extra) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userId", userId);
        row.put("username", names.get(userId));
        row.putAll(extra);
        return row;
    }

    private static double round(double v) {
        return Math.round(v * 10000) / 10000.0;
    }
}