package com.example.social.client.util;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class AppSettings {

    private static final AppSettings INSTANCE = new AppSettings();

    public static final double MIN_CARD_WIDTH = 420;
    public static final double MAX_CARD_WIDTH = 900;
    public static final double DEFAULT_CARD_WIDTH = 640;

    public static final double MIN_IMAGE_HEIGHT = 200;
    public static final double MAX_IMAGE_HEIGHT = 900;
    public static final double DEFAULT_IMAGE_HEIGHT = 520;

    private static final String KEY_CARD_WIDTH = "post.cardWidth";
    private static final String KEY_IMAGE_HEIGHT = "post.imageHeight";

    public enum SizePreset {
        SMALL(1, "Малий", 460, 280),
        NORMAL(2, "Нормальний", 640, 520),
        LARGE(3, "Великий", 760, 680),
        EXTRA_LARGE(4, "Найбільший", 900, 900);

        private final int level;
        private final String label;
        private final double cardWidth;
        private final double imageHeight;

        SizePreset(int level, String label, double cardWidth, double imageHeight) {
            this.level = level;
            this.label = label;
            this.cardWidth = cardWidth;
            this.imageHeight = imageHeight;
        }

        public int getLevel() { return level; }
        public String getLabel() { return label; }
        public double getCardWidth() { return cardWidth; }
        public double getImageHeight() { return imageHeight; }

        @Override
        public String toString() { return level + " — " + label; }
    }
    public static AppSettings getInstance() {
        return INSTANCE;
    }


    private final Path settingsFile;
    private final DoubleProperty cardWidth = new SimpleDoubleProperty(DEFAULT_CARD_WIDTH);
    private final DoubleProperty imageHeight = new SimpleDoubleProperty(DEFAULT_IMAGE_HEIGHT);

    private AppSettings() {
        settingsFile = Path.of(System.getProperty("user.home"), ".socialapp", "settings.properties");
        load();
    }

    public DoubleProperty cardWidthProperty() {
        return cardWidth;
    }

    public DoubleProperty imageHeightProperty() {
        return imageHeight;
    }

    public void setCardWidth(double value) {
        cardWidth.set(clamp(value, MIN_CARD_WIDTH, MAX_CARD_WIDTH));
        save();
    }

    public void setImageHeight(double value) {
        imageHeight.set(clamp(value, MIN_IMAGE_HEIGHT, MAX_IMAGE_HEIGHT));
        save();
    }

    public void resetToDefaults() {
        applyPreset(SizePreset.NORMAL);
    }

    public void applyPreset(SizePreset preset) {
        setCardWidth(preset.getCardWidth());
        setImageHeight(preset.getImageHeight());
    }

    /** Найближчий пресет до поточних значень — для позначення активного в UI при відкритті екрана. */
    public SizePreset closestPreset() {
        double width = cardWidth.get();
        SizePreset closest = SizePreset.NORMAL;
        double bestDiff = Double.MAX_VALUE;
        for (SizePreset preset : SizePreset.values()) {
            double diff = Math.abs(preset.getCardWidth() - width);
            if (diff < bestDiff) {
                bestDiff = diff;
                closest = preset;
            }
        }
        return closest;
    }

    private void load() {
        if (!Files.exists(settingsFile)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(settingsFile)) {
            props.load(in);
            cardWidth.set(clamp(
                    parseOrDefault(props.getProperty(KEY_CARD_WIDTH), DEFAULT_CARD_WIDTH),
                    MIN_CARD_WIDTH, MAX_CARD_WIDTH));
            imageHeight.set(clamp(
                    parseOrDefault(props.getProperty(KEY_IMAGE_HEIGHT), DEFAULT_IMAGE_HEIGHT),
                    MIN_IMAGE_HEIGHT, MAX_IMAGE_HEIGHT));
        } catch (IOException ignored) {
            // якщо файл пошкоджено — просто лишаємось на значеннях за замовчуванням
        }
    }

    private void save() {
        try {
            Files.createDirectories(settingsFile.getParent());
        } catch (IOException e) {
            return; // не критично: налаштування діють у поточній сесії, просто не збережуться
        }

        Properties props = new Properties();
        props.setProperty(KEY_CARD_WIDTH, String.valueOf(cardWidth.get()));
        props.setProperty(KEY_IMAGE_HEIGHT, String.valueOf(imageHeight.get()));

        try (OutputStream out = Files.newOutputStream(settingsFile)) {
            props.store(out, "Social Network client settings");
        } catch (IOException ignored) {
            // не критично
        }
    }

    private static double parseOrDefault(String value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}