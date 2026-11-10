package com.example.social.client.util;

import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Знімок вікна для звітів: F12 зберігає вміст вікна у PNG з утричі більшою роздільністю.
 * Інтерфейс перемальовується у збільшеному масштабі, тому текст і рамки лишаються чіткими.
 */
public final class ScreenshotUtil {

    private static final double SCALE = 3.0;
    private static final Path DIR = Path.of(System.getProperty("user.home"), "Pictures", "MySocial");
    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /** Рамка навколо знімка: світлий інтерфейс не зливається з білим папером у друкованому звіті. */
    private static final java.awt.Color FRAME_COLOR = new java.awt.Color(0x8A, 0x96, 0xA3);

    private ScreenshotUtil() {
    }

    public static void install(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F12), () -> save(scene));
    }

    public static void save(Scene scene) {
        try {
            Path file = save(scene, DIR.resolve("screen_" + LocalDateTime.now().format(NAME) + ".png"));
            System.out.println("Screenshot saved: " + file);
        } catch (IOException e) {
            System.err.println("Screenshot failed: " + e.getMessage());
        }
    }

    /** Нова папка для серії знімків, напр. ~/Pictures/MySocial/scenario_2026-11-10_22-30-00. */
    public static Path newSeriesDir(String prefix) throws IOException {
        return Files.createDirectories(DIR.resolve(prefix + "_" + LocalDateTime.now().format(NAME)));
    }

    /** Знімок вікна у вказаний файл (потік інтерфейсу). */
    public static Path save(Scene scene, Path file) throws IOException {
        SnapshotParameters params = new SnapshotParameters();
        params.setTransform(Transform.scale(SCALE, SCALE));
        params.setFill(scene.getFill() instanceof Color color ? color : Color.WHITE);
        WritableImage fxImage = scene.getRoot().snapshot(params, null);

        int width = (int) fxImage.getWidth();
        int height = (int) fxImage.getHeight();
        int[] pixels = new int[width * height];
        fxImage.getPixelReader().getPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), pixels, 0, width);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        drawFrame(image);

        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
        return file;
    }

    /** Рамка завтовшки 1 логічний піксель (з урахуванням масштабу знімка). */
    private static void drawFrame(BufferedImage image) {
        int thickness = (int) Math.round(SCALE);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(FRAME_COLOR);
            g.fillRect(0, 0, image.getWidth(), thickness);
            g.fillRect(0, image.getHeight() - thickness, image.getWidth(), thickness);
            g.fillRect(0, 0, thickness, image.getHeight());
            g.fillRect(image.getWidth() - thickness, 0, thickness, image.getHeight());
        } finally {
            g.dispose();
        }
    }
}