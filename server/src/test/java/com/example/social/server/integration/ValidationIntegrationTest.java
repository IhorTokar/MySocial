package com.example.social.server.integration;

import com.example.social.server.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Задовгі значення відхиляються з 400 і зрозумілим поясненням, а не падають у БД з 500. */
class ValidationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void tooLongPostTitleIsRejectedWithReadableMessage() throws Exception {
        TestUser me = createUser(UserRole.user);

        mvc.perform(post("/api/posts").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(postJson("x".repeat(256), "текст", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Заголовок — щонайбільше 255 символів")));
    }

    @Test
    void titleOfExactlyMaxLengthAndTenTagsAreAccepted() throws Exception {
        TestUser me = createUser(UserRole.user);
        String tags = "[" + String.join(",", Collections.nCopies(10, "\"" + "т".repeat(50) + "\"")) + "]";

        mvc.perform(post("/api/posts").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(postJson("x".repeat(255), "текст", tags)))
                .andExpect(status().isCreated());
    }

    @Test
    void tooLongOrTooManyOrEmptyTagsAreRejected() throws Exception {
        TestUser me = createUser(UserRole.user);
        String longTag = "[\"" + "т".repeat(51) + "\"]";
        String elevenTags = "[" + String.join(",", Collections.nCopies(11, "\"тег\"")) + "]";
        String emptyTag = "[\" \"]";

        for (String tags : new String[]{longTag, elevenTags, emptyTag}) {
            mvc.perform(post("/api/posts").header("Authorization", me.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content(postJson("заголовок", "текст", tags)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void tooLongCommentIsRejected() throws Exception {
        TestUser me = createUser(UserRole.user);
        long postId = jdbc.queryForObject(
                "INSERT INTO posts(user_id, text) VALUES (?, 'пост') RETURNING post_id", Long.class, me.id());

        mvc.perform(post("/api/posts/" + postId + "/comments").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + "к".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Коментар — щонайбільше 2000 символів")));
    }

    @Test
    void tooLongDisplayNameIsRejected() throws Exception {
        TestUser me = createUser(UserRole.user);

        mvc.perform(patch("/api/users/me").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"" + "і".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Ім'я — щонайбільше 100 символів")));
    }

    private static String postJson(String label, String text, String tagsJson) {
        return "{\"label\":\"" + label + "\",\"text\":\"" + text + "\",\"tags\":" + tagsJson + "}";
    }
}