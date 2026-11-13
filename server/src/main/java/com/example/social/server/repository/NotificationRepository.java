package com.example.social.server.repository;

import com.example.social.shared.dto.NotificationDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * Сповіщення «на льоту»: об'єднання підписок, вподобань, коментарів і відповідей,
 * адресованих користувачу. Окремої таблиці немає — нічого не треба синхронізувати при видаленні
 * лайка, коментаря чи акаунта.
 */
@Repository
public class NotificationRepository {

    private static final int SNIPPET_LENGTH = 80;

    /** Параметри (по порядку): me ×6. */
    private static final String EVENTS = """
            SELECT 'follow' AS type, f.follower_id AS actor_id, NULL::bigint AS post_id,
                   NULL AS snippet, f.created_at AS at
            FROM followers f
            WHERE f.following_id = ?
            UNION ALL
            SELECT 'like', l.user_id, l.post_id, LEFT(COALESCE(NULLIF(p.label, ''), p.text), %1$d), l.liked_at
            FROM post_likes l JOIN posts p ON p.post_id = l.post_id
            WHERE p.user_id = ? AND l.user_id <> p.user_id
            UNION ALL
            SELECT 'comment', c.user_id, c.post_id, LEFT(c.text, %1$d), c.created_at
            FROM comments c
            JOIN posts p ON p.post_id = c.post_id
            LEFT JOIN comments parent ON parent.comment_id = c.parent_comment_id
            WHERE p.user_id = ? AND c.user_id <> ?
              AND (parent.user_id IS NULL OR parent.user_id <> p.user_id)
            UNION ALL
            SELECT 'reply', c.user_id, c.post_id, LEFT(c.text, %1$d), c.created_at
            FROM comments c JOIN comments parent ON parent.comment_id = c.parent_comment_id
            WHERE parent.user_id = ? AND c.user_id <> ?
            """.formatted(SNIPPET_LENGTH);

    private static final String LIST = """
            SELECT e.type, e.actor_id, u.username, u.display_name, u.user_avatar_url,
                   e.post_id, e.snippet, e.at,
                   e.at > COALESCE((SELECT notifications_seen_at FROM "user" WHERE user_id = ?), '-infinity') AS unread
            FROM (%s) e
            JOIN "user" u ON u.user_id = e.actor_id
            ORDER BY e.at DESC
            LIMIT ?
            """.formatted(EVENTS);

    private static final String UNREAD_COUNT = """
            SELECT COUNT(*) FROM (%s) e
            WHERE e.at > COALESCE((SELECT notifications_seen_at FROM "user" WHERE user_id = ?), '-infinity')
            """.formatted(EVENTS);

    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<NotificationDto> latest(long userId, int limit) {
        return jdbc.query(LIST, (rs, rowNum) -> {
                    long rawPostId = rs.getLong("post_id");
                    Long postId = rs.wasNull() ? null : rawPostId;   // wasNull — одразу після читання поля
                    Timestamp at = rs.getTimestamp("at");
                    return new NotificationDto(rs.getString("type"), rs.getLong("actor_id"),
                            rs.getString("username"), rs.getString("display_name"), rs.getString("user_avatar_url"),
                            postId, rs.getString("snippet"), at.toLocalDateTime(), rs.getBoolean("unread"));
                },
                userId, userId, userId, userId, userId, userId, userId, limit);
    }

    public long unreadCount(long userId) {
        Long count = jdbc.queryForObject(UNREAD_COUNT, Long.class,
                userId, userId, userId, userId, userId, userId, userId);
        return count == null ? 0 : count;
    }

    public void markSeen(long userId) {
        jdbc.update("UPDATE \"user\" SET notifications_seen_at = now() WHERE user_id = ?", userId);
    }
}