package com.example.social.server.integration;

import com.example.social.server.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Видалення власного акаунта разом з усім, що з ним пов'язано. */
class DeleteAccountIntegrationTest extends IntegrationTestBase {

    private static final String ME = "/api/users/me";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void accountWithRepliesInChatCanBeDeleted() throws Exception {
        TestUser me = createUser(UserRole.user);
        TestUser friend = createUser(UserRole.user);

        // Діалог з відповіддю: батьківське повідомлення старше, тому при видаленні йде першим
        long question = jdbc.queryForObject(
                "INSERT INTO messages(sender_id, receiver_id, message) VALUES (?, ?, 'Як справи?') RETURNING message_id",
                Long.class, friend.id(), me.id());
        jdbc.update("INSERT INTO messages(sender_id, receiver_id, message, parent_message_id) VALUES (?, ?, 'Добре!', ?)",
                me.id(), friend.id(), question);

        long post = jdbc.queryForObject(
                "INSERT INTO posts(user_id, text) VALUES (?, 'мій пост') RETURNING post_id", Long.class, me.id());
        jdbc.update("INSERT INTO post_likes(user_id, post_id) VALUES (?, ?)", friend.id(), post);
        jdbc.update("INSERT INTO comments(post_id, user_id, text) VALUES (?, ?, 'клас')", post, friend.id());
        jdbc.update("INSERT INTO followers(follower_id, following_id) VALUES (?, ?)", friend.id(), me.id());

        mvc.perform(delete(ME).header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        assertEquals(0, count("SELECT count(*) FROM \"user\" WHERE user_id = ?", me.id()));
        assertEquals(0, count("SELECT count(*) FROM messages WHERE sender_id = ? OR receiver_id = ?", me.id(), me.id()));
        assertEquals(0, count("SELECT count(*) FROM posts WHERE user_id = ?", me.id()));
        assertEquals(1, count("SELECT count(*) FROM \"user\" WHERE user_id = ?", friend.id()));
    }

    @Test
    void deletedMessageLeavesReplyWithoutQuote() {
        TestUser a = createUser(UserRole.user);
        TestUser b = createUser(UserRole.user);
        long parent = jdbc.queryForObject(
                "INSERT INTO messages(sender_id, receiver_id, message) VALUES (?, ?, 'питання') RETURNING message_id",
                Long.class, a.id(), b.id());
        long reply = jdbc.queryForObject(
                "INSERT INTO messages(sender_id, receiver_id, message, parent_message_id) VALUES (?, ?, 'відповідь', ?) RETURNING message_id",
                Long.class, b.id(), a.id(), parent);

        jdbc.update("DELETE FROM messages WHERE message_id = ?", parent);

        assertNull(jdbc.queryForObject("SELECT parent_message_id FROM messages WHERE message_id = ?", Long.class, reply));
    }

    @Test
    void wrongPasswordKeepsAccount() throws Exception {
        TestUser me = createUser(UserRole.user);

        mvc.perform(delete(ME).header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isBadRequest());

        assertEquals(1, count("SELECT count(*) FROM \"user\" WHERE user_id = ?", me.id()));
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}