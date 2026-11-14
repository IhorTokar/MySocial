package com.example.social.server.integration;

import com.example.social.server.entity.UserRole;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Завантаження файлів і посилання на медіа в постах. */
class MediaUploadIntegrationTest extends IntegrationTestBase {

    @Test
    void realImageIsStoredWithExtensionFromContent() throws Exception {
        TestUser me = createUser(UserRole.user);
        // PNG під чужим іменем і з неправдивим Content-Type — важить лише вміст
        MockMultipartFile file = new MockMultipartFile("file", "photo.exe", "application/octet-stream", png());

        String json = mvc.perform(multipart("/api/messages/upload").file(file).header("Authorization", me.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String url = JsonPath.read(json, "$.url");
        assertTrue(url.matches("^/media/[0-9a-f-]{36}\\.png$"), url);
    }

    @Test
    void htmlWithImageContentTypeIsRejected() throws Exception {
        TestUser me = createUser(UserRole.user);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.html", "image/png",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/users/me/avatar").file(file).header("Authorization", me.bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postWithForeignMediaUrlIsRejected() throws Exception {
        TestUser me = createUser(UserRole.user);

        mvc.perform(post("/api/posts").header("Authorization", me.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"дивіться\",\"mediaUrl\":\"http://evil.example/track.png\"}"))
                .andExpect(status().isBadRequest());
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }
}