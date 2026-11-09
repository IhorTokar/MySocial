package com.example.social.server.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Зіставлення спільнот між двома запусками Louvain/Leiden.
 * Номери спільнот при кожному запуску нові, тому «та сама» спільнота шукається за складом:
 * та, з якою найбільший коефіцієнт Жаккара |A ∩ B| / |A ∪ B|.
 */
public final class CommunityMatcher {

    private CommunityMatcher() {
    }

    public record Match(long community, double jaccard) {
    }

    public static double jaccard(Collection<Long> a, Collection<Long> b) {
        Set<Long> setA = new HashSet<>(a);
        Set<Long> setB = new HashSet<>(b);
        if (setA.isEmpty() && setB.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (Long id : setA) {
            if (setB.contains(id)) {
                intersection++;
            }
        }
        return (double) intersection / (setA.size() + setB.size() - intersection);
    }

    /**
     * @param members    склад спільноти, для якої шукаємо попередницю
     * @param candidates спільноти попереднього знімка: номер → склад
     * @param minOverlap нижчий збіг вважається іншою спільнотою
     * @return найкращий збіг; при рівності — спільнота з меншим номером (детерміновано)
     */
    public static Optional<Match> bestMatch(Collection<Long> members,
                                            Map<Long, ? extends Collection<Long>> candidates,
                                            double minOverlap) {
        Match best = null;
        for (Map.Entry<Long, ? extends Collection<Long>> candidate : candidates.entrySet()) {
            double overlap = jaccard(members, candidate.getValue());
            if (overlap < minOverlap) {
                continue;
            }
            if (best == null || overlap > best.jaccard()
                    || (overlap == best.jaccard() && candidate.getKey() < best.community())) {
                best = new Match(candidate.getKey(), overlap);
            }
        }
        return Optional.ofNullable(best);
    }
}