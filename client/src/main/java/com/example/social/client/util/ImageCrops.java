package com.example.social.client.util;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Вирізати ділянку завантаженого зображення, зменшити й записати в тимчасовий PNG (для обкладинки й аватара). */
public final class ImageCrops {

    private ImageCrops() {
    }

    /**
     * @param area   ділянка в пікселях оригіналу
     * @param maxOut найбільша ширина результату; висота — пропорційно (менші ділянки не збільшуються)
     */
    public static Path toPng(Image image, Rectangle2D area, int maxOut, String prefix) throws IOException {
        PixelReader reader = image.getPixelReader();
        if (reader == null) {
            throw new IOException("Зображення ще не завантажилось");
        }
        int sx = clamp((int) Math.round(area.getMinX()), 0, (int) image.getWidth() - 1);
        int sy = clamp((int) Math.round(area.getMinY()), 0, (int) image.getHeight() - 1);
        int sw = clamp((int) Math.round(area.getWidth()), 1, (int) image.getWidth() - sx);
        int sh = clamp((int) Math.round(area.getHeight()), 1, (int) image.getHeight() - sy);

        int[] pixels = new int[sw * sh];
        reader.getPixels(sx, sy, sw, sh, PixelFormat.getIntArgbInstance(), pixels, 0, sw);
        BufferedImage source = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, sw, sh, pixels, 0, sw);

        int outW = Math.min(maxOut, sw);
        int outH = Math.max(1, (int) Math.round(outW * (double) sh / sw));
        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);   // прозорі ділянки — білим
            g.fillRect(0, 0, outW, outH);
            g.drawImage(source, 0, 0, outW, outH, null);
        } finally {
            g.dispose();
        }

        Path file = Files.createTempFile(prefix, ".png");
        ImageIO.write(out, "png", file.toFile());
        return file;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}