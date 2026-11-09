package com.example.social.server.integration;

import com.example.social.server.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Безпека «наскрізь»: справжній ланцюжок фільтрів (JwtAuthFilter + правила SecurityConfig),
 * контролери, сервіси й тестова PostgreSQL. Кожен тест створює власних користувачів.
 */
class SecurityIntegrationTest extends IntegrationTestBase {

    private static final String WEIGHTS = "/api/admin/weights";
    private static final String ME = "/api/users/me";

    private static String loginJson(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    // ===== Хто має доступ до адмінки =====

    @Test
    void anonymousRequestToAdminApiIsForbidden() throws Exception {
        mvc.perform(get(WEIGHTS)).andExpect(status().isForbidden());
    }

    @Test
    void regularUserCannotUseAdminOrDevApi() throws Exception {
        TestUser user = createUser(UserRole.user);

        mvc.perform(get(WEIGHTS).header("Authorization", user.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/dev/backfill-sentiment").header("Authorization", user.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadWeights() throws Exception {
        TestUser admin = createUser(UserRole.admin);

        mvc.perform(get(WEIGHTS).header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.popularity").isNumber());
    }

    @Test
    void invalidWeightsAreRejectedWithReadableMessage() throws Exception {
        TestUser admin = createUser(UserRole.admin);
        String body = "{\"popularity\":0.1,\"itemCf\":0.3,\"graphSim\":0.35,\"semantic\":0.3,"
                + "\"recency\":0.1,\"followedBonus\":0.3}";

        mvc.perform(put(WEIGHTS).header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("1.150")));
    }

    @Test
    void invalidTokenIsTreatedAsAnonymous() throws Exception {
        mvc.perform(get(ME).header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isForbidden());
    }

    // ===== Зміни діють одразу, без повторного входу =====

    @Test
    void roleChangeTakesEffectWithoutNewLogin() throws Exception {
        TestUser user = createUser(UserRole.user);
        mvc.perform(get(WEIGHTS).header("Authorization", user.bearer())).andExpect(status().isForbidden());

        changePrivate(user.id(), p -> p.setRole(UserRole.admin));

        // той самий токен — роль читається з БД при кожному запиті
        mvc.perform(get(WEIGHTS).header("Authorization", user.bearer())).andExpect(status().isOk());
    }

    @Test
    void blockedUserLosesAccessImmediatelyAndCannotLogIn() throws Exception {
        TestUser admin = createUser(UserRole.admin);
        TestUser user = createUser(UserRole.user);
        mvc.perform(get(ME).header("Authorization", user.bearer())).andExpect(status().isOk());

        mvc.perform(post("/api/admin/users/" + user.id() + "/block").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"спам\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true));

        mvc.perform(get(ME).header("Authorization", user.bearer())).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.username(), PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Акаунт заблоковано: спам"));

        // дія потрапила в журнал
        mvc.perform(get("/api/admin/audit?limit=1").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$[0].action").value("USER_BLOCK"))
                .andExpect(jsonPath("$[0].targetId").value(user.id()));
    }

    @Test
    void passwordResetRevokesOldTokens() throws Exception {
        TestUser admin = createUser(UserRole.admin);
        TestUser user = createUser(UserRole.user);
        mvc.perform(get(ME).header("Authorization", user.bearer())).andExpect(status().isOk());

        mvc.perform(post("/api/admin/users/" + user.id() + "/password").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"brandnew123\"}"))
                .andExpect(status().isOk());

        // старий токен більше не діє, старий пароль теж
        mvc.perform(get(ME).header("Authorization", user.bearer())).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.username(), PASSWORD)))
                .andExpect(status().isUnauthorized());

        // новий пароль дає новий робочий токен
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.username(), "brandnew123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    // ===== Вхід =====

    @Test
    void loginReturnsRoleForTheClient() throws Exception {
        TestUser admin = createUser(UserRole.admin);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(admin.username(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("admin"))
                .andExpect(jsonPath("$.userId").value(admin.id()));
    }

    @Test
    void wrongPasswordGives401() throws Exception {
        TestUser user = createUser(UserRole.user);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(user.username(), "wrong-password")))
                .andExpect(status().isUnauthorized());
    }
}