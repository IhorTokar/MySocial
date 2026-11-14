package com.example.social.server.service;

import com.example.social.server.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Тип зображення — за вмістом; посилання — лише на файли цього сервера. */
class MediaFilesTest {

    private static final String UUID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    void extensionComesFromContentNotFromName() throws IOException {
        assertEquals(".png", MediaFiles.imageExtension(image("png")));
        assertEquals(".jpg", MediaFiles.imageExtension(image("jpg")));
        assertEquals(".gif", MediaFiles.imageExtension(image("gif")));
        assertEquals(".bmp", MediaFiles.imageExtension(image("bmp")));
    }

    @Test
    void htmlDisguisedAsImageIsRejected() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        assertThrows(BadRequestException.class, () -> MediaFiles.imageExtension(html));
    }

    @Test
    void emptyFileIsRejected() {
        assertThrows(BadRequestException.class, () -> MediaFiles.imageExtension(new byte[0]));
    }

    @Test
    void localMediaUrlIsAccepted() {
        assertEquals("/media/" + UUID + ".png", MediaFiles.requireLocalUrl(" /media/" + UUID + ".png "));
    }

    @Test
    void emptyUrlMeansNoMedia() {
        assertNull(MediaFiles.requireLocalUrl(null));
        assertNull(MediaFiles.requireLocalUrl("  "));
    }

    @Test
    void foreignOrTrickyUrlsAreRejected() {
        for (String url : new String[]{
                "http://evil.example/track.png",
                "https://localhost:8080/media/" + UUID + ".png",
                "//evil.example/" + UUID + ".png",
                "/media/../application.yml",
                "/media/" + UUID + ".png?x=1",
                "/media/not-a-uuid.png",
                "file:///etc/passwd"}) {
            assertThrows(BadRequestException.class, () -> MediaFiles.requireLocalUrl(url), url);
        }
    }

    private static byte[] image(String format) throws IOException {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return bytes.toByteArray();
    }
}