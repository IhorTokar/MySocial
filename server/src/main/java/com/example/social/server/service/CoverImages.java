package com.example.social.server.service;

import com.example.social.server.exception.BadRequestException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Обкладинка профілю завжди має формат 3:1. Якщо зображення іншої форми — обрізається по центру,
 * завелике — зменшується до 1500×500. Результат зберігається як JPEG.
 */
public final class CoverImages {

    public static final int RATIO = 3;
    public static final int WIDTH = 1500;
    public static final int HEIGHT = WIDTH / RATIO;
    public static final int MIN_WIDTH = 600;
    public static final int MIN_HEIGHT = MIN_WIDTH / RATIO;
    /** Захист від «бомб»: маленький файл з величезними розмірами з'їв би всю пам'ять при декодуванні. */
    private static final int MAX_SOURCE_SIDE = 10_000;
    private static final float JPEG_QUALITY = 0.88f;

    private CoverImages() {
    }

    /** @return JPEG 3:1, щонайбільше 1500×500 */
    public static byte[] normalize(byte[] data) {
        BufferedImage source = read(data);
        int w = source.getWidth();
        int h = source.getHeight();

        // найбільший прямокутник 3:1, що вміщається в зображення, — по центру
        int cropW = w;
        int cropH = w / RATIO;
        if (cropH > h) {
            cropH = h;
            cropW = h * RATIO;
        }
        if (cropW < MIN_WIDTH || cropH < MIN_HEIGHT) {
            throw new BadRequestException("Зображення замале: потрібно щонайменше "
                    + MIN_WIDTH + "×" + MIN_HEIGHT + " (рекомендовано " + WIDTH + "×" + HEIGHT + ")");
        }
        int x = (w - cropW) / 2;
        int y = (h - cropH) / 2;

        int outW = Math.min(WIDTH, cropW);
        int outH = outW / RATIO;
        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // прозорі ділянки PNG/GIF у JPEG стають білими, а не чорними
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, outW, outH);
            g.drawImage(source, 0, 0, outW, outH, x, y, x + cropW, y + cropH, null);
        } finally {
            g.dispose();
        }
        return toJpeg(out);
    }

    private static BufferedImage read(byte[] data) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw new BadRequestException("Формат зображення не підтримується (PNG, JPEG, GIF, BMP)");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                // розміри читаються із заголовка, до декодування пікселів
                if (reader.getWidth(0) > MAX_SOURCE_SIDE || reader.getHeight(0) > MAX_SOURCE_SIDE) {
                    throw new BadRequestException("Зображення завелике: щонайбільше "
                            + MAX_SOURCE_SIDE + " пікселів по кожній стороні");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BadRequestException("Не вдалося прочитати зображення");
        }
    }

    private static byte[] toJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException e) {
            throw new IllegalStateException("Не вдалося зберегти обкладинку", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}