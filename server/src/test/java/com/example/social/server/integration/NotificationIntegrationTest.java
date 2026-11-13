package com.example.social.server.integration;

import com.example.social.server.entity.UserRole;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Сповіщення: хто підписався, вподобав, прокоментував, відповів — і лічильник непрочитаних. */
class NotificationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void collectsEventsAddressedToMeAndCountsUnread() throws Exception {
        TestUser me = createUser(UserRole.user);
        TestUser fan = createUser(UserRole.user);
        TestUser critic = createUser(UserRole.user);

        long myPost = jdbc.queryForObject(
                "INSERT INTO posts(user_id, label, text) VALUES (?, 'Мій пост', 'текст') RETURNING post_id",
                Long.class, me.id());
        long theirPost = jdbc.queryForObject(
                "INSERT INTO posts(user_id, text) VALUES (?, 'чужий') RETURNING post_id", Long.class, critic.id());

        jdbc.update("INSERT INTO followers(follower_id, following_id, created_at) VALUES (?, ?, now() - interval '3 minutes')",
                fan.id(), me.id());
        jdbc.update("INSERT INTO post_likes(user_id, post_id, liked_at) VALUES (?, ?, now() - interval '2 minutes')",
                fan.id(), myPost);
        jdbc.update("INSERT INTO post_likes(user_id, post_id) VALUES (?, ?)", me.id(), myPost);   // власний — не рахується
        long myComment = jdbc.queryForObject(
                "INSERT INTO comments(post_id, user_id, text, created_at) VALUES (?, ?, 'мій коментар', now() - interval '90 seconds') RETURNING comment_id",
                Long.class, theirPost, me.id());
        jdbc.update("INSERT INTO comments(post_id, user_id, text, parent_comment_id, created_at) VALUES (?, ?, 'відповідь', ?, now() - interval '1 minute')",
                theirPost, critic.id(), myComment);
        jdbc.update("INSERT INTO comments(post_id, user_id, text) VALUES (?, ?, 'гарний пост')", myPost, critic.id());

        String json = mvc.perform(get("/api/notifications").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> types = JsonPath.read(json, "$[*].type");
        assertEquals(List.of("comment", "reply", "like", "follow"), types);   // від найновішого
        assertEquals("Мій пост", JsonPath.read(json, "$[2].snippet"));
        assertEquals(fan.username(), JsonPath.read(json, "$[3].actorUsername"));

        mvc.perform(get("/api/notifications/unread-count").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.unread").value(4));

        mvc.perform(post("/api/notifications/seen").header("Authorization", me.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$.unread").value(0));
        mvc.perform(get("/api/notifications").header("Authorization", me.bearer()))
                .andExpect(jsonPath("$[0].unread").value(false));
    }
}