package com.example.social.server.service;

import com.example.social.server.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Обкладинка: будь-яке зображення приводиться до 3:1 і не більше 1500×500. */
class CoverImagesTest {

    @Test
    void tallImageIsCroppedToThreeToOneAndScaledDown() throws IOException {
        BufferedImage result = normalize(png(2400, 1800, Color.BLUE));
        assertEquals(1500, result.getWidth());
        assertEquals(500, result.getHeight());
    }

    @Test
    void wideImageIsCroppedByWidth() throws IOException {
        BufferedImage result = normalize(png(4000, 600, Color.GREEN));
        assertEquals(1500, result.getWidth());
        assertEquals(500, result.getHeight());
    }

    @Test
    void smallerImageKeepsItsSizeButGetsTheRatio() throws IOException {
        BufferedImage result = normalize(png(900, 400, Color.RED));
        assertEquals(900, result.getWidth());
        assertEquals(300, result.getHeight());
    }

    @Test
    void cropIsCentered() throws IOException {
        // верхня й нижня третини — чорні, середня — біла: після обрізки 3:1 лишається лише середина
        BufferedImage source = new BufferedImage(1500, 1500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 500, 1500, 500);
        g.dispose();

        BufferedImage result = normalize(encode(source));
        int rgb = result.getRGB(750, 250) & 0xFFFFFF;
        assertTrue((rgb & 0xFF) > 240, "центр обкладинки має бути білим");
    }

    @Test
    void tooSmallImageIsRejected() {
        assertThrows(BadRequestException.class, () -> CoverImages.normalize(png(500, 400, Color.RED)));
    }

    @Test
    void notAnImageIsRejected() {
        assertThrows(BadRequestException.class, () -> CoverImages.normalize("hello".getBytes()));
    }

    private static BufferedImage normalize(byte[] data) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(CoverImages.normalize(data)));
    }

    private static byte[] png(int width, int height, Color color) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return encode(image);
    }

    private static byte[] encode(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}