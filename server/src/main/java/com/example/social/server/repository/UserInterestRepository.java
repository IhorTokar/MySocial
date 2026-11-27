package com.example.social.server.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Теми, які людина обрала при першому вході (холодний старт стрічки), і чи її про це вже питали.
 * Таблиця user_interest_tags і колонка "user".interests_asked_at — міграція V15.
 */
@Repository
public class UserInterestRepository {

    /** Тег і скільки постів його мають — варіанти для вибору тем. */
    public record TagCount(String tag, long posts) {
    }

    private final JdbcTemplate jdbc;

    public UserInterestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> findTagNames(long userId) {
        return jdbc.queryForList("SELECT t.name FROM user_interest_tags ui JOIN tags t ON t.tag_id = ui.tag_id "
                + "WHERE ui.user_id = ? ORDER BY t.name", String.class, userId);
    }

    /** Замінює обрані теми; невідомі назви тегів пропускаються (обирати можна лише з наявних). */
    public void replace(long userId, List<String> tagNames) {
        jdbc.update("DELETE FROM user_interest_tags WHERE user_id = ?", userId);
        if (tagNames.isEmpty()) {
            return;
        }
        String[] names = tagNames.toArray(new String[0]);
        jdbc.update(con -> {
            var ps = con.prepareStatement("INSERT INTO user_interest_tags (user_id, tag_id) "
                    + "SELECT ?, tag_id FROM tags WHERE name = ANY(?) ON CONFLICT DO NOTHING");
            ps.setLong(1, userId);
            ps.setArray(2, con.createArrayOf("varchar", names));
            return ps;
        });
    }

    public boolean wasAsked(long userId) {
        Boolean asked = jdbc.queryForObject(
                "SELECT interests_asked_at IS NOT NULL FROM \"user\" WHERE user_id = ?", Boolean.class, userId);
        return Boolean.TRUE.equals(asked);
    }

    public void markAsked(long userId) {
        jdbc.update("UPDATE \"user\" SET interests_asked_at = now() WHERE user_id = ? AND interests_asked_at IS NULL",
                userId);
    }

    /** Чи є в людини власні сигнали для стрічки: вподобання, коментарі або підписки. */
    public boolean hasActivity(long userId) {
        Boolean active = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM post_likes WHERE user_id = ?) "
                        + "OR EXISTS (SELECT 1 FROM comments WHERE user_id = ?) "
                        + "OR EXISTS (SELECT 1 FROM followers WHERE follower_id = ?)",
                Boolean.class, userId, userId, userId);
        return Boolean.TRUE.equals(active);
    }

    /** Найпоширеніші теги мережі — з них людина обирає свої теми. */
    public List<TagCount> popularTags(int limit) {
        return jdbc.query("SELECT t.name, COUNT(*) AS posts FROM post_tags pt JOIN tags t ON t.tag_id = pt.tag_id "
                        + "GROUP BY t.name ORDER BY posts DESC, t.name LIMIT ?",
                (rs, i) -> new TagCount(rs.getString("name"), rs.getLong("posts")), limit);
    }

    /** Найновіші чужі пости з будь-яким із тегів — кандидати стрічки за обраними темами. */
    public List<Long> recentPostIdsWithTags(long userId, List<String> tagNames, int limit) {
        return postIdsWithTags(userId, tagNames, limit, "p.created_date DESC, p.post_id DESC");
    }

    /** Найвподобаніші чужі пости з цими тегами — «зразки» тем, доки людина нічого не вподобала сама. */
    public List<Long> topLikedPostIdsWithTags(long userId, List<String> tagNames, int limit) {
        return postIdsWithTags(userId, tagNames, limit,
                "(SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.post_id) DESC, p.post_id DESC");
    }

    private List<Long> postIdsWithTags(long userId, List<String> tagNames, int limit, String order) {
        if (tagNames.isEmpty()) {
            return List.of();
        }
        String[] names = tagNames.toArray(new String[0]);
        // order — лише одна з двох констант вище, не дані користувача
        return jdbc.query(con -> {
            var ps = con.prepareStatement("SELECT p.post_id FROM posts p WHERE p.user_id <> ? AND EXISTS ("
                    + "SELECT 1 FROM post_tags pt JOIN tags t ON t.tag_id = pt.tag_id "
                    + "WHERE pt.post_id = p.post_id AND t.name = ANY(?)) ORDER BY " + order + " LIMIT ?");
            ps.setLong(1, userId);
            ps.setArray(2, con.createArrayOf("varchar", names));
            ps.setInt(3, limit);
            return ps;
        }, (rs, i) -> rs.getLong(1));
    }
}
