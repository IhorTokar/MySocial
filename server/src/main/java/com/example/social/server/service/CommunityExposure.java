package com.example.social.server.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Наскільки видача рекомендацій тримає користувача всередині його спільноти.
 *
 * <ul>
 *   <li><b>частка своєї спільноти</b> — частка постів, автор яких у тій самій спільноті, що й користувач;</li>
 *   <li><b>випадкова частка</b> — та сама частка, якби пости бралися навмання з усіх постів мережі
 *       (крім власних): Σ постів авторів своєї спільноти / Σ усіх постів. Відношення першої до другої —
 *       <b>підсилення</b> (lift): 1 — стрічка не зважає на спільноти, більше — замикає в них;</li>
 *   <li><b>ентропія спільнот</b> — H(спільнот авторів) / log₂(кількість спільнот мережі):
 *       0 — усі пости з однієї спільноти, 1 — порівну з усіх;</li>
 *   <li>скільки різних спільнот і тем у видачі, частка постів від тих, на кого користувач підписаний;</li>
 *   <li><b>збіг з інтересами</b> — частка постів, що мають хоча б один з улюблених тегів користувача
 *       (найчастіших серед постів, які він вподобав): чи не жертвує розбавлення відповідністю інтересам.</li>
 * </ul>
 * Автори без спільноти (не потрапили в граф) рахуються як окрема «чужа» група.
 */
public final class CommunityExposure {

    /** Група для авторів без спільноти. */
    private static final Object NO_COMMUNITY = new Object();

    private CommunityExposure() {
    }

    /** Один пост видачі: автор і його теги. */
    public record Item(long authorId, Collection<String> tags) {
    }

    /** Показники однієї видачі (одного списку рекомендацій одного користувача). */
    public record ListStats(int size, double ownShare, double followedShare, int communities, double entropy,
                            int distinctTags) {
    }

    /**
     * @param userCommunity    спільнота користувача
     * @param labels           userId → спільнота
     * @param followed         на кого підписаний користувач
     * @param totalCommunities кількість спільнот у мережі (для нормалізації ентропії)
     */
    public static ListStats of(List<Item> items, Object userCommunity, Map<Long, ?> labels, Set<Long> followed,
                               int totalCommunities) {
        if (items.isEmpty()) {
            return new ListStats(0, 0, 0, 0, 0, 0);
        }
        int own = 0;
        int fromFollowed = 0;
        Map<Object, Integer> byCommunity = new HashMap<>();
        Set<String> tags = new HashSet<>();
        for (Item item : items) {
            Object community = labels.get(item.authorId());
            if (community != null && community.equals(userCommunity)) {
                own++;
            }
            if (followed.contains(item.authorId())) {
                fromFollowed++;
            }
            byCommunity.merge(Objects.requireNonNullElse(community, NO_COMMUNITY), 1, Integer::sum);
            tags.addAll(item.tags());
        }
        int n = items.size();
        return new ListStats(n, own / (double) n, fromFollowed / (double) n, byCommunity.size(),
                normalizedEntropy(byCommunity.values(), totalCommunities), tags.size());
    }

    /**
     * Частка постів своєї спільноти, якби пости обиралися навмання з усіх постів мережі, крім власних.
     *
     * @param postsByAuthor userId → кількість його постів
     */
    public static double randomOwnShare(long userId, Map<Long, ?> labels, Map<Long, Integer> postsByAuthor) {
        Object community = labels.get(userId);
        long own = 0;
        long all = 0;
        for (Map.Entry<Long, Integer> e : postsByAuthor.entrySet()) {
            if (e.getKey() == userId) {
                continue;
            }
            all += e.getValue();
            if (community != null && community.equals(labels.get(e.getKey()))) {
                own += e.getValue();
            }
        }
        return all == 0 ? 0 : own / (double) all;
    }

    /** H / log₂(categories); 0 — одна група, 1 — рівномірно по всіх. */
    public static double normalizedEntropy(Collection<Integer> counts, int categories) {
        double total = counts.stream().mapToInt(Integer::intValue).sum();
        if (total == 0 || categories < 2) {
            return 0;
        }
        double h = 0;
        for (int c : counts) {
            if (c > 0) {
                double p = c / total;
                h -= p * Math.log(p) / Math.log(2);
            }
        }
        return Math.min(1, h / (Math.log(categories) / Math.log(2)));
    }

    /** Частка постів, що мають хоча б один тег з {@code interests}; 0 — якщо інтересів немає. */
    public static double interestMatch(List<Item> items, Set<String> interests) {
        if (items.isEmpty() || interests.isEmpty()) {
            return 0;
        }
        long matched = items.stream().filter(i -> i.tags().stream().anyMatch(interests::contains)).count();
        return matched / (double) items.size();
    }

    /** n найчастіших тегів (при рівності — за абеткою). */
    public static Set<String> topTags(Map<String, Integer> counts, int n) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(n)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** Середнє й вибіркове стандартне відхилення. */
    public static double[] meanSd(List<Double> values) {
        if (values.isEmpty()) {
            return new double[]{0, 0};
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double ss = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum();
        return new double[]{mean, Math.sqrt(ss / Math.max(1, values.size() - 1))};
    }

    /** Перші k індексів за спаданням оцінки; при рівності — менший індекс (вищий у вихідному порядку). */
    public static List<Integer> topK(double[] scores, int k) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            order.add(i);
        }
        order.sort((a, b) -> scores[a] != scores[b] ? Double.compare(scores[b], scores[a]) : Integer.compare(a, b));
        return order.subList(0, Math.min(k, order.size()));
    }

}