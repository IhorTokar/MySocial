package com.example.social.server.integration;

import com.example.social.server.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import com.jayway.jsonpath.JsonPath;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** «Забув пароль»: заявка без входу, розгляд адміністратором, вхід з тимчасовим паролем. */
class PasswordResetRequestIntegrationTest extends IntegrationTestBase {

    private static final String SUBMIT = "/api/auth/password-reset-requests";
    private static final String ADMIN_LIST = "/api/admin/password-requests";

    private static String body(String login) {
        return "{\"login\":\"" + login + "\",\"contact\":\"+380671234567\",\"message\":\"забув пароль\"}";
    }

    @Test
    void anonymousCanSubmitAndUnknownLoginLooksTheSame() throws Exception {
        TestUser user = createUser(UserRole.user);

        // і для існуючого, і для вигаданого логіна — однакова відповідь
        mvc.perform(post(SUBMIT).contentType(MediaType.APPLICATION_JSON).content(body(user.username())))
                .andExpect(status().isAccepted());
        mvc.perform(post(SUBMIT).contentType(MediaType.APPLICATION_JSON).content(body("nobody_" + user.id())))
                .andExpect(status().isAccepted());
    }

    @Test
    void onlyAdminSeesRequests() throws Exception {
        TestUser user = createUser(UserRole.user);
        mvc.perform(get(ADMIN_LIST).header("Authorization", user.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminResolvesRequestAndUserLogsInWithTemporaryPassword() throws Exception {
        TestUser admin = createUser(UserRole.admin);
        TestUser user = createUser(UserRole.user);
        // вхід за email теж знаходить акаунт
        mvc.perform(post(SUBMIT).contentType(MediaType.APPLICATION_JSON).content(body(user.username() + "@test.local")))
                .andExpect(status().isAccepted());
        // повторна заявка не створює дубліката
        mvc.perform(post(SUBMIT).contentType(MediaType.APPLICATION_JSON).content(body(user.username())))
                .andExpect(status().isAccepted());

        String pending = mvc.perform(get(ADMIN_LIST).header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(pending, "$[?(@.userId == " + user.id() + ")].requestId");
        List<String> contacts = JsonPath.read(pending, "$[?(@.userId == " + user.id() + ")].contact");
        assertEquals(1, ids.size());   // одна заявка, а не дві
        assertEquals("+380671234567", contacts.get(0));
        long requestId = ids.get(0).longValue();

        mvc.perform(post(ADMIN_LIST + "/" + requestId + "/resolve").header("Authorization", admin.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"temporary123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("resolved"));

        // заявка зникла зі списку нових, а з тимчасовим паролем можна увійти
        String afterResolve = mvc.perform(get(ADMIN_LIST).header("Authorization", admin.bearer()))
                .andReturn().getResponse().getContentAsString();
        List<Number> stillPending = JsonPath.read(afterResolve, "$[?(@.userId == " + user.id() + ")].requestId");
        assertTrue(stillPending.isEmpty());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user.username() + "\",\"password\":\"temporary123\"}"))
                .andExpect(status().isOk());

        // розглянуту заявку не можна закрити вдруге
        mvc.perform(post(ADMIN_LIST + "/" + requestId + "/reject").header("Authorization", admin.bearer()))
                .andExpect(status().isConflict());
    }
}