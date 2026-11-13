package com.example.social.server.repository;

import com.example.social.shared.dto.TrendingTagDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Запити для бічної панелі «Гарячі теми» / «Кого почитати» — агрегати, тож через JdbcTemplate. */
@Repository
public class DiscoveryRepository {

    /**
     * Активність по тегах за період: нові пости, вподобання й коментарі до постів із тегом.
     * Вага: пост = 3, коментар = 2, вподобання = 1 (новий пост на тему — найсильніший сигнал).
     */
    private static final String TRENDING_TAGS = """
            WITH recent_posts AS (
                SELECT pt.tag_id, COUNT(*) AS cnt
                FROM post_tags pt JOIN posts p ON p.post_id = pt.post_id
                WHERE p.created_date >= ?
                GROUP BY pt.tag_id),
            recent_likes AS (
                SELECT pt.tag_id, COUNT(*) AS cnt
                FROM post_tags pt JOIN post_likes l ON l.post_id = pt.post_id
                WHERE l.liked_at >= ?
                GROUP BY pt.tag_id),
            recent_comments AS (
                SELECT pt.tag_id, COUNT(*) AS cnt
                FROM post_tags pt JOIN comments c ON c.post_id = pt.post_id
                WHERE c.created_at >= ?
                GROUP BY pt.tag_id)
            SELECT t.name,
                   COALESCE(rp.cnt, 0) AS posts,
                   COALESCE(rl.cnt, 0) AS likes,
                   COALESCE(rc.cnt, 0) AS comments
            FROM tags t
            LEFT JOIN recent_posts rp ON rp.tag_id = t.tag_id
            LEFT JOIN recent_likes rl ON rl.tag_id = t.tag_id
            LEFT JOIN recent_comments rc ON rc.tag_id = t.tag_id
            WHERE rp.cnt IS NOT NULL OR rl.cnt IS NOT NULL OR rc.cnt IS NOT NULL
            ORDER BY 3 * COALESCE(rp.cnt, 0) + 2 * COALESCE(rc.cnt, 0) + COALESCE(rl.cnt, 0) DESC, t.name
            LIMIT ?
            """;

    /** «Друзі друзів»: кого читають мої підписки, а я — ні. Значення — скільки моїх підписок його читає. */
    private static final String FOLLOWED_BY_MY_FOLLOWINGS = """
            SELECT f2.following_id AS user_id, COUNT(*) AS cnt
            FROM followers f1
            JOIN followers f2 ON f2.follower_id = f1.following_id
            WHERE f1.follower_id = ?
              AND f2.following_id <> ?
              AND f2.following_id NOT IN (SELECT following_id FROM followers WHERE follower_id = ?)
            GROUP BY f2.following_id
            ORDER BY cnt DESC, f2.following_id
            LIMIT ?
            """;

    /** Найпопулярніші автори (за кількістю читачів), на яких я ще не підписаний. */
    private static final String POPULAR_NOT_FOLLOWED = """
            SELECT u.user_id, COUNT(f.follower_id) AS cnt
            FROM "user" u
            LEFT JOIN followers f ON f.following_id = u.user_id
            WHERE u.user_id <> ?
              AND u.user_id NOT IN (SELECT following_id FROM followers WHERE follower_id = ?)
            GROUP BY u.user_id
            ORDER BY cnt DESC, u.user_id
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;

    public DiscoveryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TrendingTagDto> trendingTags(LocalDateTime since, int limit) {
        Timestamp from = Timestamp.valueOf(since);
        return jdbc.query(TRENDING_TAGS,
                (rs, rowNum) -> new TrendingTagDto(rs.getString("name"),
                        rs.getLong("posts"), rs.getLong("likes"), rs.getLong("comments")),
                from, from, from, limit);
    }

    /** @return userId → скільки моїх підписок його читає (у порядку спадання) */
    public Map<Long, Long> followedByMyFollowings(long userId, int limit) {
        Map<Long, Long> result = new LinkedHashMap<>();
        jdbc.query(FOLLOWED_BY_MY_FOLLOWINGS,
                rs -> { result.put(rs.getLong("user_id"), rs.getLong("cnt")); },
                userId, userId, userId, limit);
        return result;
    }

    /** @return userId → кількість читачів (у порядку спадання) */
    public Map<Long, Long> popularNotFollowed(long userId, int limit) {
        Map<Long, Long> result = new LinkedHashMap<>();
        jdbc.query(POPULAR_NOT_FOLLOWED,
                rs -> { result.put(rs.getLong("user_id"), rs.getLong("cnt")); },
                userId, userId, limit);
        return result;
    }

    public Set<Long> blockedUserIds() {
        return new HashSet<>(jdbc.queryForList("SELECT user_id FROM user_private WHERE blocked", Long.class));
    }
}