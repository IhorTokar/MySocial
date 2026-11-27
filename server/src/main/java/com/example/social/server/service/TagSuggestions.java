package com.example.social.server.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Підказки тегів для нового поста: «голосування» найближчих за змістом постів.
 * Кожен сусід віддає свою схожість із текстом кожному зі своїх тегів; частка тегу — його сума,
 * поділена на суму схожостей усіх сусідів. Так тег, що стоїть на багатьох дуже схожих постах,
 * отримує частку близьку до 1, а випадковий тег одного далекого поста — близьку до 0.
 */
public final class TagSuggestions {

    private TagSuggestions() {
    }

    /** Схожий пост: косинусна схожість із текстом і його теги. */
    public record Neighbour(double similarity, Collection<String> tags) {
    }

    /**
     * @param share частка «голосів» за тег (0..1)
     * @param posts на скількох із найближчих постів стоїть цей тег
     */
    public record Suggestion(String tag, double share, int posts) {
    }

    /**
     * @param minShare тег пропонується, лише якщо набрав щонайменше таку частку
     * @param exclude  теги, які автор уже вказав (без «#», регістр не важливий)
     */
    public static List<Suggestion> rank(List<Neighbour> neighbours, int limit, double minShare,
                                        Collection<String> exclude) {
        Map<String, Double> votes = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> spelling = new HashMap<>();   // як тег написано вперше — у такому вигляді й пропонуємо
        double total = 0;
        for (Neighbour n : neighbours) {
            double weight = Math.max(0, n.similarity());
            if (weight == 0 || n.tags() == null || n.tags().isEmpty()) {
                continue;
            }
            total += weight;
            for (String tag : new LinkedHashSet<>(n.tags())) {
                String key = tag.toLowerCase();
                spelling.putIfAbsent(key, tag);
                votes.merge(key, weight, Double::sum);
                counts.merge(key, 1, Integer::sum);
            }
        }
        if (total == 0) {
            return List.of();
        }
        Set<String> skip = new HashSet<>();
        if (exclude != null) {
            exclude.forEach(t -> skip.add(t.toLowerCase()));
        }
        List<Suggestion> result = new ArrayList<>();
        for (Map.Entry<String, Double> e : votes.entrySet()) {
            double share = e.getValue() / total;
            if (share >= minShare && !skip.contains(e.getKey())) {
                result.add(new Suggestion(spelling.get(e.getKey()), Math.round(share * 1000) / 1000.0,
                        counts.get(e.getKey())));
            }
        }
        result.sort(Comparator.comparingDouble(Suggestion::share).reversed().thenComparing(Suggestion::tag));
        return result.size() > limit ? new ArrayList<>(result.subList(0, limit)) : result;
    }
}
