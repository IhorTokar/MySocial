package com.example.social.server.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Структурні характеристики розбиття графа підписок на спільноти. Підписка A → B — ребро;
 * для модульності, зв'язності, провідності й коефіцієнта участі напрям не враховується
 * (так само, як у проєкції для Louvain/Leiden), для PageRank — враховується.
 *
 * <ul>
 *   <li><b>модульність</b> Q = Σ_c [ e_c / m − (vol_c / 2m)² ] — наскільки зв'язків усередині спільнот
 *       більше, ніж у випадковому графі з тими самими ступенями;</li>
 *   <li><b>щільність</b> — частка можливих підписок усередині спільноти, що справді є: e_c / (n(n − 1));</li>
 *   <li><b>провідність</b> (conductance) — частка зв'язків спільноти, що ведуть назовні:
 *       cut / min(vol_c, 2m − vol_c); 0 — замкнена спільнота, ближче до 1 — розмита;</li>
 *   <li><b>PageRank</b> — впливовість: на кого підписуються впливові користувачі;</li>
 *   <li><b>коефіцієнт участі</b> P = 1 − Σ_s (k_s / k)² — наскільки зв'язки користувача розподілені між
 *       різними спільнотами (0 — усі в одній, ближче до 1 — «міст»).</li>
 * </ul>
 * Враховуються лише ребра, обидва кінці яких мають спільноту.
 */
public final class CommunityStructure {

    private CommunityStructure() {
    }

    public record Edge(long from, long to) {
    }

    /** Характеристики однієї спільноти. */
    public record Stats(Object community, int size, int internalEdges, int cut, double density, double conductance) {
    }

    public static double modularity(List<Edge> edges, Map<Long, ?> labels) {
        List<Edge> kept = within(edges, labels);
        double m = kept.size();
        if (m == 0) {
            return 0;
        }
        Map<Object, Double> internal = new HashMap<>();
        Map<Object, Double> volume = new HashMap<>();
        for (Edge e : kept) {
            Object a = labels.get(e.from());
            Object b = labels.get(e.to());
            volume.merge(a, 1.0, Double::sum);
            volume.merge(b, 1.0, Double::sum);
            if (a.equals(b)) {
                internal.merge(a, 1.0, Double::sum);
            }
        }
        double q = 0;
        for (Map.Entry<Object, Double> v : volume.entrySet()) {
            double share = v.getValue() / (2 * m);
            q += internal.getOrDefault(v.getKey(), 0.0) / m - share * share;
        }
        return q;
    }

    /** Характеристики кожної спільноти (від більших до менших). */
    public static List<Stats> stats(List<Edge> edges, Map<Long, ?> labels) {
        List<Edge> kept = within(edges, labels);
        Map<Object, Integer> size = new HashMap<>();
        labels.values().forEach(l -> size.merge(l, 1, Integer::sum));
        Map<Object, Integer> internal = new HashMap<>();
        Map<Object, Integer> cut = new HashMap<>();
        for (Edge e : kept) {
            Object a = labels.get(e.from());
            Object b = labels.get(e.to());
            if (a.equals(b)) {
                internal.merge(a, 1, Integer::sum);
            } else {
                cut.merge(a, 1, Integer::sum);
                cut.merge(b, 1, Integer::sum);
            }
        }
        double totalVolume = 2.0 * kept.size();
        List<Stats> result = new ArrayList<>();
        for (Map.Entry<Object, Integer> s : size.entrySet()) {
            int n = s.getValue();
            int in = internal.getOrDefault(s.getKey(), 0);
            int out = cut.getOrDefault(s.getKey(), 0);
            double volume = 2.0 * in + out;
            double smaller = Math.min(volume, totalVolume - volume);
            result.add(new Stats(s.getKey(), n, in, out,
                    n < 2 ? 0 : in / (double) (n * (n - 1)),
                    smaller == 0 ? 0 : out / smaller));
        }
        result.sort((x, y) -> Integer.compare(y.size(), x.size()));
        return result;
    }

    /** Скільки спільнот (з двох і більше учасників) розпадаються на незв'язні частини. */
    public static int disconnected(List<Edge> edges, Map<Long, ?> labels) {
        Map<Long, List<Long>> adjacency = new HashMap<>();
        for (Edge e : within(edges, labels)) {
            if (labels.get(e.from()).equals(labels.get(e.to()))) {
                adjacency.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
                adjacency.computeIfAbsent(e.to(), k -> new ArrayList<>()).add(e.from());
            }
        }
        Map<Object, List<Long>> members = new HashMap<>();
        labels.forEach((user, label) -> members.computeIfAbsent(label, k -> new ArrayList<>()).add(user));

        int broken = 0;
        for (List<Long> group : members.values()) {
            if (group.size() < 2) {
                continue;
            }
            Set<Long> seen = new HashSet<>();
            Deque<Long> queue = new ArrayDeque<>(List.of(group.get(0)));
            seen.add(group.get(0));
            while (!queue.isEmpty()) {
                for (Long next : adjacency.getOrDefault(queue.poll(), List.of())) {
                    if (seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            if (seen.size() < group.size()) {
                broken++;
            }
        }
        return broken;
    }

    /**
     * PageRank за підписками (A → B передає «голос» B), коефіцієнт згасання 0.85;
     * ті, хто ні на кого не підписаний, розподіляють свою вагу порівну між усіма.
     */
    public static Map<Long, Double> pageRank(List<Edge> edges, Collection<Long> nodes) {
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(nodes));
        int n = ids.size();
        Map<Long, Double> rank = new HashMap<>();
        if (n == 0) {
            return rank;
        }
        Map<Long, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) {
            index.put(ids.get(i), i);
        }
        List<List<Integer>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new ArrayList<>());
        }
        for (Edge e : edges) {
            Integer a = index.get(e.from());
            Integer b = index.get(e.to());
            if (a != null && b != null && !a.equals(b)) {
                out.get(a).add(b);
            }
        }
        double damping = 0.85;
        double[] r = new double[n];
        java.util.Arrays.fill(r, 1.0 / n);
        for (int iteration = 0; iteration < 100; iteration++) {
            double[] next = new double[n];
            double dangling = 0;
            for (int i = 0; i < n; i++) {
                if (out.get(i).isEmpty()) {
                    dangling += r[i];
                } else {
                    double share = r[i] / out.get(i).size();
                    for (int j : out.get(i)) {
                        next[j] += share;
                    }
                }
            }
            double delta = 0;
            for (int i = 0; i < n; i++) {
                next[i] = (1 - damping) / n + damping * (next[i] + dangling / n);
                delta += Math.abs(next[i] - r[i]);
            }
            r = next;
            if (delta < 1e-10) {
                break;
            }
        }
        for (int i = 0; i < n; i++) {
            rank.put(ids.get(i), r[i]);
        }
        return rank;
    }

    /** Коефіцієнт участі кожного користувача (за неорієнтованими зв'язками) і його ступінь. */
    public static Map<Long, double[]> participation(List<Edge> edges, Map<Long, ?> labels) {
        Map<Long, Map<Object, Integer>> byCommunity = new HashMap<>();
        for (Edge e : within(edges, labels)) {
            byCommunity.computeIfAbsent(e.from(), k -> new HashMap<>()).merge(labels.get(e.to()), 1, Integer::sum);
            byCommunity.computeIfAbsent(e.to(), k -> new HashMap<>()).merge(labels.get(e.from()), 1, Integer::sum);
        }
        Map<Long, double[]> result = new HashMap<>();
        byCommunity.forEach((user, counts) -> {
            double degree = counts.values().stream().mapToInt(Integer::intValue).sum();
            double sum = 0;
            for (int k : counts.values()) {
                sum += (k / degree) * (k / degree);
            }
            result.put(user, new double[]{1 - sum, degree, counts.size()});
        });
        return result;
    }

    /** Кількість підписок зі спільноти-рядка до спільноти-стовпця. */
    public static Map<Object, Map<Object, Integer>> matrix(List<Edge> edges, Map<Long, ?> labels) {
        Map<Object, Map<Object, Integer>> result = new HashMap<>();
        for (Edge e : within(edges, labels)) {
            result.computeIfAbsent(labels.get(e.from()), k -> new HashMap<>())
                    .merge(labels.get(e.to()), 1, Integer::sum);
        }
        return result;
    }

    private static List<Edge> within(List<Edge> edges, Map<Long, ?> labels) {
        List<Edge> kept = new ArrayList<>();
        for (Edge e : edges) {
            if (e.from() != e.to() && labels.containsKey(e.from()) && labels.containsKey(e.to())) {
                kept.add(e);
            }
        }
        return kept;
    }
}