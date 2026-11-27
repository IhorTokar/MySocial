package com.example.social.server.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Перевірка векторів текстів: чи справді найближчі за вектором пости — на ту саму тему.
 * Для кожного поста вибірки беруться k найближчих за косинусною схожістю, і рахується частка тих,
 * що мають з ним спільний тег (точність@k). Її порівнюють із тим, що дали б k випадкових постів:
 * частка постів мережі зі спільним тегом. Відношення двох чисел — у скільки разів вектори кращі за випадок.
 */
public final class EmbeddingQuality {

    private EmbeddingQuality() {
    }

    /** Результат для одного тегу: скільки постів вибірки з ним і середня точність@k для них. */
    public record TagResult(String tag, int posts, double precision, double baseline) {
    }

    /**
     * @param precision       середня частка сусідів зі спільним тегом
     * @param baseline        середня частка постів мережі зі спільним тегом (те саме для випадкових k постів)
     * @param lift            precision / baseline
     * @param sameAuthorShare частка сусідів того самого автора (щоб видно було, що схожість — не лише «стиль автора»)
     */
    public record Result(int samples, int k, int population, double precision, double baseline, double lift,
                         double sameAuthorShare, List<TagResult> byTag) {
    }

    /**
     * @param sample     перевірені пости
     * @param neighbours пост → його найближчі за вектором (до k, без нього самого)
     * @param tags       пост → теги, для всіх постів сукупності (і вибірки, і сусідів)
     * @param authors    пост → автор (може бути неповним — тоді «той самий автор» не рахується)
     */
    public static Result evaluate(Collection<Long> sample, Map<Long, List<Long>> neighbours,
                                  Map<Long, ? extends Collection<String>> tags, Map<Long, Long> authors, int k) {
        // тег → пости з ним: для точного «випадкового» рівня
        Map<String, Set<Long>> postsByTag = new HashMap<>();
        tags.forEach((post, ts) -> ts.forEach(t -> postsByTag.computeIfAbsent(t, x -> new HashSet<>()).add(post)));
        int population = tags.size();

        double precisionSum = 0;
        double baselineSum = 0;
        long neighbourCount = 0;
        long sameAuthor = 0;
        int evaluated = 0;
        Map<String, double[]> perTag = new TreeMap<>();   // тег → {постів, сума точності, сума випадкового рівня}
        for (Long post : sample) {
            List<Long> near = neighbours.getOrDefault(post, List.of());
            Collection<String> own = tags.get(post);
            if (near.isEmpty() || own == null || own.isEmpty() || population < 2) {
                continue;
            }
            int hits = 0;
            for (Long n : near) {
                if (shares(own, tags.get(n))) {
                    hits++;
                }
                Long a = authors.get(post);
                if (a != null && a.equals(authors.get(n))) {
                    sameAuthor++;
                }
            }
            neighbourCount += near.size();
            double precision = hits / (double) near.size();

            Set<Long> sameTag = new HashSet<>();
            own.forEach(t -> sameTag.addAll(postsByTag.getOrDefault(t, Set.of())));
            double baseline = (sameTag.size() - 1) / (double) (population - 1);   // без самого поста

            precisionSum += precision;
            baselineSum += baseline;
            evaluated++;
            for (String t : own) {
                double[] acc = perTag.computeIfAbsent(t, x -> new double[3]);
                acc[0]++;
                acc[1] += precision;
                acc[2] += baseline;
            }
        }
        if (evaluated == 0) {
            return new Result(0, k, population, 0, 0, 0, 0, List.of());
        }
        double precision = precisionSum / evaluated;
        double baseline = baselineSum / evaluated;
        List<TagResult> byTag = new ArrayList<>();
        perTag.forEach((t, acc) -> byTag.add(new TagResult(t, (int) acc[0], round(acc[1] / acc[0]), round(acc[2] / acc[0]))));
        byTag.sort((a, b) -> Integer.compare(b.posts(), a.posts()));
        return new Result(evaluated, k, population, round(precision), round(baseline),
                baseline > 0 ? round(precision / baseline) : 0, round(sameAuthor / (double) neighbourCount), byTag);
    }

    static boolean shares(Collection<String> a, Collection<String> b) {
        if (b == null) {
            return false;
        }
        for (String t : a) {
            if (b.contains(t)) {
                return true;
            }
        }
        return false;
    }

    static double round(double v) {
        return Math.round(v * 10000) / 10000.0;
    }

    /** Звіт для JSON (порядок полів сталий). */
    public static Map<String, Object> toMap(Result r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("samples", r.samples());
        m.put("k", r.k());
        m.put("population", r.population());
        m.put("precisionAtK", r.precision());
        m.put("randomBaseline", r.baseline());
        m.put("lift", r.lift());
        m.put("sameAuthorShare", r.sameAuthorShare());
        m.put("byTag", r.byTag());
        return m;
    }
}
