package com.example.social.server.integration;

import com.example.social.server.exception.BadRequestException;
import com.example.social.server.service.SeedEvolutionService;
import com.example.social.server.service.SeedGeneratorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Еволюція мережі без знімків (Neo4j і NLP тут не запускаються): ролі мають поводитися так, як задумано,
 * а результат — відтворюватися за тим самим зерном.
 */
class SeedEvolutionIntegrationTest extends IntegrationTestBase {

    private static final String SEED_USERS =
            "SELECT user_id FROM user_private WHERE email LIKE '%" + SeedGeneratorService.EMAIL_DOMAIN + "'";

    @Autowired
    private SeedEvolutionService evolution;

    @Autowired
    private SeedGeneratorService generator;

    @Autowired
    private JdbcTemplate jdbc;

    /** 40 користувачів у 4 спільнотах (2 вузькі), 2 тижні бази + 4 тижні еволюції, по 10% кожної ролі. */
    private static SeedEvolutionService.Params params() {
        SeedGeneratorService.Params network =
                new SeedGeneratorService.Params(40, 4, 14, 4, 6, 12, 0.8, 0.5, 0.1, 0.15, 0.4, 0.05, 42);
        return new SeedEvolutionService.Params(network, 4, 7, 0.1, 0.1, 0.1, 0.1, 0.1);
    }

    @AfterEach
    void removeGenerated() {
        generator.cleanup();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rolesBehaveAsDesigned() {
        Map<String, Object> result = evolution.run(params(), 0, false);

        List<Map<String, Object>> epochs = (List<Map<String, Object>>) result.get("epochs");
        assertEquals(5, epochs.size(), "база + 4 тижні");
        Map<String, Map<String, Object>> personas = (Map<String, Map<String, Object>>) result.get("personas");
        for (String role : List.of("drifter", "escaper", "migrant", "newcomer", "leaver")) {
            assertEquals(4, personas.get(role).get("count"), role);
        }
        assertEquals(44L, jdbc.queryForObject(SEED_USERS.replace("SELECT user_id", "SELECT count(*)"), Long.class),
                "40 + 4 новачки");

        List<Double> drifter = (List<Double>) personas.get("drifter").get("personalHomogeneity");
        assertTrue(last(drifter) > drifter.get(0) + 0.1, "затягування звужує інтереси: " + drifter);

        List<Double> escaper = (List<Double>) personas.get("escaper").get("personalHomogeneity");
        assertTrue(last(escaper) < escaper.get(0) - 0.2, "вихід з ехо-камери розширює інтереси: " + escaper);

        List<Double> leaver = (List<Double>) personas.get("leaver").get("actionsPerUser");
        assertTrue(last(leaver) < leaver.get(1) * 0.3, "активність того, хто йде, згасає: " + leaver);

        List<Double> newcomer = (List<Double>) personas.get("newcomer").get("actionsPerUser");
        assertNull(newcomer.get(0), "у базовій мережі новачків ще немає");
        assertTrue(last(newcomer) > 0);
    }

    @Test
    void newWeeksArePublishedAfterBaseNetwork() {
        evolution.run(params(), 0, false);

        long fresh = jdbc.queryForObject("SELECT count(*) FROM posts WHERE user_id IN (" + SEED_USERS + ") "
                + "AND created_date > now() - interval '28 days'", Long.class);
        long future = jdbc.queryForObject("SELECT count(*) FROM posts WHERE user_id IN (" + SEED_USERS + ") "
                + "AND created_date > now() + interval '1 minute'", Long.class);
        assertTrue(fresh > 100, "пости тижнів еволюції: " + fresh);
        assertEquals(0, future, "жодного поста в майбутньому");
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM followers WHERE follower_id = following_id", Long.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sameSeedReproducesSameEvolution() {
        Map<String, Object> first = evolution.run(params(), 0, false);
        generator.cleanup();
        Map<String, Object> second = evolution.run(params(), 0, false);

        assertEquals(activity(first), activity(second));
        assertEquals(withoutIds(first), withoutIds(second), "ті самі ролі, логіни й показники (id — інші)");
    }

    @Test
    void invalidParametersAreRejected() {
        SeedGeneratorService.Params network = params().network();
        assertThrows(BadRequestException.class, () -> evolution.run(
                new SeedEvolutionService.Params(network, 0, 7, 0.1, 0.1, 0.1, 0.1, 0.1), 0, false));
        assertThrows(BadRequestException.class, () -> evolution.run(
                new SeedEvolutionService.Params(network, 4, 7, 0.3, 0.3, 0.3, 0, 0), 0, false));
        assertEquals(0L, jdbc.queryForObject(SEED_USERS.replace("SELECT user_id", "SELECT count(*)"), Long.class));
    }

    private static double last(List<Double> values) {
        for (int i = values.size() - 1; i >= 0; i--) {
            if (values.get(i) != null) {
                return values.get(i);
            }
        }
        throw new AssertionError("немає значень: " + values);
    }

    /** Ролі без id користувачів: після прибирання база видає нові id, а все інше має збігтися. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> withoutIds(Map<String, Object> result) {
        Map<String, Object> personas = new java.util.LinkedHashMap<>();
        ((Map<String, Map<String, Object>>) result.get("personas")).forEach((role, row) -> {
            Map<String, Object> copy = new java.util.LinkedHashMap<>(row);
            copy.put("users", ((List<Map<String, Object>>) row.get("users")).stream()
                    .map(u -> {
                        Map<String, Object> user = new java.util.LinkedHashMap<>(u);
                        user.remove("userId");
                        return user;
                    }).toList());
            personas.put(role, copy);
        });
        return personas;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> activity(Map<String, Object> result) {
        return ((List<Map<String, Object>>) result.get("epochs")).stream().map(e -> e.get("activity")).toList();
    }
}