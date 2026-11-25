package com.example.social.server.service;

import com.example.social.server.service.CommunityStructure.Edge;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Дані з PostgreSQL для аналізу спільнот: підписки, логіни, теги постів, справжні спільноти генератора. */
@Component
public class CommunityData {

    private final JdbcTemplate jdbc;

    public CommunityData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Edge> follows() {
        List<Edge> edges = new ArrayList<>();
        for (String row : jdbc.queryForList("SELECT follower_id || '|' || following_id FROM followers", String.class)) {
            String[] p = row.split("\\|");
            edges.add(new Edge(Long.parseLong(p[0]), Long.parseLong(p[1])));
        }
        return edges;
    }

    public Map<Long, String> usernames(Collection<Long> ids) {
        Map<Long, String> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        String list = ids.stream().map(String::valueOf).collect(Collectors.joining(","));   // лише числа
        for (String row : jdbc.queryForList(
                "SELECT user_id || '|' || username FROM \"user\" WHERE user_id IN (" + list + ")", String.class)) {
            int bar = row.indexOf('|');
            result.put(Long.parseLong(row.substring(0, bar)), row.substring(bar + 1));
        }
        return result;
    }

    /** userId → {тег → кількість постів з ним}. */
    public Map<Long, Map<String, Integer>> tagsByUser() {
        Map<Long, Map<String, Integer>> result = new HashMap<>();
        for (String row : jdbc.queryForList("SELECT p.user_id || '|' || t.name FROM posts p "
                + "JOIN post_tags pt ON pt.post_id = p.post_id JOIN tags t ON t.tag_id = pt.tag_id", String.class)) {
            int bar = row.indexOf('|');
            result.computeIfAbsent(Long.parseLong(row.substring(0, bar)), k -> new HashMap<>())
                    .merge(row.substring(bar + 1), 1, Integer::sum);
        }
        return result;
    }

    /**
     * Справжні спільноти згенерованих користувачів основної мережі. Ті, хто за роллю переходить між
     * спільнотами (затягування, вихід, міграція), не враховуються: для них «правильної» відповіді немає.
     */
    public Map<Long, Integer> groundTruth() {
        Map<Long, Integer> result = new HashMap<>();
        for (String row : jdbc.queryForList("SELECT g.user_id || '|' || g.community FROM seed_ground_truth g "
                        + "JOIN user_private p ON p.user_id = g.user_id "
                        + "WHERE g.role IN ('regular', 'newcomer', 'leaver') AND p.email LIKE ?", String.class,
                "%" + SeedGeneratorService.EMAIL_DOMAIN)) {
            String[] p = row.split("\\|");
            result.put(Long.parseLong(p[0]), Integer.parseInt(p[1]));
        }
        return result;
    }
}