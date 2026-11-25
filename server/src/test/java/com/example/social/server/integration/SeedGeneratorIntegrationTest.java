package com.example.social.server.integration;

import com.example.social.server.entity.UserRole;
import com.example.social.server.exception.BadRequestException;
import com.example.social.server.service.SeedGeneratorService;
import com.example.social.server.service.SeedGeneratorService.Params;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Генератор реалістичних даних (tools/report/seed.py): цілісність даних, вплив параметрів,
 * відтворюваність за зерном і прибирання. Neo4j і NLP тут не потрібні — генератор пише лише в PostgreSQL.
 */
class SeedGeneratorIntegrationTest extends IntegrationTestBase {

    private static final String SEED_USERS =
            "SELECT user_id FROM user_private WHERE email LIKE '%" + SeedGeneratorService.EMAIL_DOMAIN + "'";

    @Autowired
    private SeedGeneratorService generator;

    @Autowired
    private JdbcTemplate jdbc;

    /** Невелика мережа: 30 користувачів, 3 спільноти, 10 днів — генерується за секунди. */
    private static Params params(double homophily, long seed) {
        return new Params(30, 3, 10, 3, 5, 8, homophily, 0.5, 0.1, 0.15, 0.4, 0.05, seed);
    }

    @AfterEach
    void removeGenerated() {
        generator.cleanup();
    }

    @Test
    void generatedNetworkIsConsistent() throws Exception {
        Map<String, Object> summary = generator.generate(params(0.8, 42));

        assertEquals(30, summary.get("usersCreated"));
        assertEquals(30L, count(SEED_USERS.replace("SELECT user_id", "SELECT count(*)")));
        assertEquals(((Number) summary.get("postsCreated")).longValue(),
                count("SELECT count(*) FROM posts WHERE user_id IN (" + SEED_USERS + ")"));
        assertEquals(((Number) summary.get("likesCreated")).longValue(),
                count("SELECT count(*) FROM post_likes WHERE user_id IN (" + SEED_USERS + ")"));

        assertEquals(0L, count("SELECT count(*) FROM posts p WHERE p.user_id IN (" + SEED_USERS + ") "
                + "AND NOT EXISTS (SELECT 1 FROM post_tags t WHERE t.post_id = p.post_id)"), "кожен пост має тему");
        assertEquals(0L, count("SELECT count(*) FROM followers WHERE follower_id = following_id"), "без підписок на себе");
        assertEquals(0L, count("SELECT count(*) FROM post_likes l JOIN posts p ON p.post_id = l.post_id "
                + "WHERE l.user_id = p.user_id AND l.user_id IN (" + SEED_USERS + ")"), "без вподобань власних постів");
        assertEquals(0L, count("SELECT count(*) FROM posts WHERE user_id IN (" + SEED_USERS + ") "
                        + "AND (created_date > now() + interval '1 minute' OR created_date < now() - interval '11 days')"),
                "дати постів — у межах заданих днів, не в майбутньому");

        // під будь-яким згенерованим акаунтом можна увійти
        String username = jdbc.queryForObject("SELECT username FROM \"user\" WHERE user_id IN (" + SEED_USERS + ") LIMIT 1",
                String.class);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + SeedGeneratorService.SEED_PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void homophilyControlsShareOfFollowsInsideCommunity() {
        double high = share(generator.generate(params(0.9, 7)));
        generator.cleanup();
        double low = share(generator.generate(params(0.1, 7)));

        assertTrue(high > 0.7, "при гомофілії 0.9 більшість підписок — усередині спільноти, а вийшло " + high);
        assertTrue(high > low + 0.3, "гомофілія має помітно змінювати структуру: " + high + " проти " + low);
    }

    @Test
    void sameSeedReproducesSameNetwork() {
        Map<String, Object> first = withoutDuration(generator.generate(params(0.8, 42)));
        List<String> firstNames = seedUsernames();
        generator.cleanup();
        Map<String, Object> second = withoutDuration(generator.generate(params(0.8, 42)));

        assertEquals(first, second, "той самий seed — ті самі кількості, спільноти й тональність");
        assertEquals(firstNames, seedUsernames());
    }

    @Test
    void cleanupRemovesOnlyGeneratedAccounts() {
        TestUser regular = createUser(UserRole.user);
        generator.generate(params(0.8, 1));

        assertEquals(30, generator.cleanup().get("usersDeleted"));
        assertEquals(0L, count(SEED_USERS.replace("SELECT user_id", "SELECT count(*)")));
        assertEquals(1L, count("SELECT count(*) FROM \"user\" WHERE user_id = " + regular.id()));
    }

    @Test
    void invalidParametersAreRejected() {
        assertThrows(BadRequestException.class,
                () -> generator.generate(new Params(5, 2, 10, 3, 5, 8, 0.8, 0.5, 0.1, 0.15, 0.4, 0.05, 1)));
        assertThrows(BadRequestException.class,
                () -> generator.generate(new Params(30, 11, 10, 3, 5, 8, 0.8, 0.5, 0.1, 0.15, 0.4, 0.05, 1)));
        assertThrows(BadRequestException.class,
                () -> generator.generate(new Params(30, 3, 10, 3, 5, 8, 1.5, 0.5, 0.1, 0.15, 0.4, 0.05, 1)));
        assertEquals(0L, count(SEED_USERS.replace("SELECT user_id", "SELECT count(*)")), "нічого не створено");
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private static double share(Map<String, Object> summary) {
        return ((Number) summary.get("followsWithinCommunityShare")).doubleValue();
    }

    private static Map<String, Object> withoutDuration(Map<String, Object> summary) {
        Map<String, Object> copy = new LinkedHashMap<>(summary);
        copy.remove("durationSeconds");
        return copy;
    }

    private List<String> seedUsernames() {
        return jdbc.queryForList("SELECT username FROM \"user\" WHERE user_id IN (" + SEED_USERS + ") ORDER BY username",
                String.class);
    }
}