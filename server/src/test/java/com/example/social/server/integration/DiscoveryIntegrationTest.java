package com.example.social.server.integration;

import com.example.social.server.entity.UserRole;
import com.example.social.server.service.UserSimilarityService;
import com.example.social.shared.dto.SimilarUserDto;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Бічна панель: гарячі теми й кого почитати. Neo4j підмінено — схожість задається в тесті. */
class DiscoveryIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private UserSimilarityService userSimilarityService;

    @Test
    void freshTagAppearsInTrends() throws Exception {
        TestUser author = createUser(UserRole.user);
        String tag = "ittag" + UUID.randomUUID().toString().substring(0, 6);
        long postId = insertPost(author.id(), "пост про " + tag);
        long tagId = jdbc.queryForObject("INSERT INTO tags(name) VALUES (?) RETURNING tag_id", Long.class, tag);
        jdbc.update("INSERT INTO post_tags(post_id, tag_id) VALUES (?, ?)", postId, tagId);

        String json = mvc.perform(get("/api/discover/trends?limit=20").header("Authorization", author.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> posts = JsonPath.read(json, "$[?(@.tag == '" + tag + "')].posts");
        assertEquals(1, posts.size());
        assertEquals(1, posts.get(0).intValue());
    }

    @Test
    void suggestsPeopleFollowedByMyFollowingsAndSimilarOnes() throws Exception {
        TestUser me = createUser(UserRole.user);
        TestUser friend = createUser(UserRole.user);
        TestUser friendOfFriend = createUser(UserRole.user);
        TestUser similar = createUser(UserRole.user);
        TestUser blocked = createUser(UserRole.user);

        follow(me.id(), friend.id());
        follow(friend.id(), friendOfFriend.id());
        follow(friend.id(), blocked.id());
        changePrivate(blocked.id(), p -> p.setBlocked(true));
        when(userSimilarityService.getSimilarUsers(eq(me.id()), anyInt(), eq(true)))
                .thenReturn(List.of(new SimilarUserDto(similar.id(), similar.username(), 0.9)));

        String json = mvc.perform(get("/api/discover/people?limit=20").header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(json, "$[*].userId");
        List<Long> userIds = ids.stream().map(Number::longValue).toList();

        assertTrue(userIds.contains(friendOfFriend.id()));
        assertTrue(userIds.contains(similar.id()));
        assertFalse(userIds.contains(me.id()), "себе не пропонуємо");
        assertFalse(userIds.contains(friend.id()), "на цього вже підписаний");
        assertFalse(userIds.contains(blocked.id()), "заблокованих не пропонуємо");

        // перші два місця — по одному з кожного джерела, з поясненням
        assertEquals(similar.id(), userIds.get(0));
        assertEquals("Схожі інтереси", JsonPath.read(json, "$[0].reason"));
        assertEquals(friendOfFriend.id(), userIds.get(1));
        assertEquals("Читає ваша підписка", JsonPath.read(json, "$[1].reason"));
    }

    @Test
    void worksWithoutGraphSimilarity() throws Exception {
        TestUser me = createUser(UserRole.user);
        when(userSimilarityService.getSimilarUsers(anyLong(), anyInt(), eq(true)))
                .thenThrow(new IllegalStateException("Neo4j недоступний"));

        // новий користувач без підписок: лишаються популярні автори
        mvc.perform(get("/api/discover/people").header("Authorization", me.bearer()))
                .andExpect(status().isOk());
    }

    private long insertPost(long userId, String text) {
        return jdbc.queryForObject("INSERT INTO posts(user_id, text) VALUES (?, ?) RETURNING post_id",
                Long.class, userId, text);
    }

    private void follow(long follower, long following) {
        jdbc.update("INSERT INTO followers(follower_id, following_id) VALUES (?, ?)", follower, following);
    }
}