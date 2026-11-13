package com.example.social.server.integration;

import com.example.social.server.entity.UserRole;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Вкладка «Відстежувані»: лише пости підписок, від найновіших, посторінково. */
class FollowingFeedIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void showsOnlyFollowedAuthorsNewestFirst() throws Exception {
        TestUser me = createUser(UserRole.user);
        TestUser followed = createUser(UserRole.user);
        TestUser stranger = createUser(UserRole.user);
        jdbc.update("INSERT INTO followers(follower_id, following_id) VALUES (?, ?)", me.id(), followed.id());

        long older = insertPost(followed.id(), "2 days");
        long newer = insertPost(followed.id(), "1 hour");
        insertPost(stranger.id(), "1 minute");   // найновіший, але автора я не читаю

        String json = mvc.perform(get("/api/posts/feed/following").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(json, "$[*].postId");
        assertEquals(List.of(newer, older), ids.stream().map(Number::longValue).toList());

        // друга сторінка з одного поста — це старіший пост
        String page2 = mvc.perform(get("/api/posts/feed/following?limit=1&offset=1").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> page2Ids = JsonPath.read(page2, "$[*].postId");
        assertEquals(List.of(older), page2Ids.stream().map(Number::longValue).toList());
    }

    private long insertPost(long userId, String age) {
        return jdbc.queryForObject(
                "INSERT INTO posts(user_id, text, created_date) VALUES (?, 'пост', now() - ?::interval) RETURNING post_id",
                Long.class, userId, age);
    }
}