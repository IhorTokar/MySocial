package com.example.social.client.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javafx.scene.Scene;
import javafx.scene.paint.Color;

public class ThemeManager {

    public enum Theme {
        LIGHT("/css/theme-light.css"),
        DARK("/css/theme-dark.css"),
        CUSTOM(null);

        private final String resourcePath;

        Theme(String resourcePath) {
            this.resourcePath = resourcePath;
        }
    }

    private static final ThemeManager INSTANCE = new ThemeManager();

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    private static final String KEY_THEME = "theme.name";
    private static final String KEY_CUSTOM_BG = "theme.custom.bg";
    private static final String KEY_CUSTOM_ACCENT = "theme.custom.accent";
    private static final String KEY_CUSTOM_TEXT = "theme.custom.text";

    private static final String DEFAULT_CUSTOM_BG = "#1E1E2E";
    private static final String DEFAULT_CUSTOM_ACCENT = "#A78BFA";
    private static final String DEFAULT_CUSTOM_TEXT = "#F5F5F7";

    private final Path themePropsFile;
    private final Path customCssFile;

    private Theme theme = Theme.LIGHT;
    private String customBg = DEFAULT_CUSTOM_BG;
    private String customAccent = DEFAULT_CUSTOM_ACCENT;
    private String customText = DEFAULT_CUSTOM_TEXT;

    private ThemeManager() {
        Path dir = Path.of(System.getProperty("user.home"), ".socialapp");
        themePropsFile = dir.resolve("theme.properties");
        customCssFile = dir.resolve("theme-custom.css");
        load();
        if (theme == Theme.CUSTOM) {
            writeCustomCssFile();
        }
    }

    public Theme getTheme() {
        return theme;
    }

    public String getCustomBg() { return customBg; }
    public String getCustomAccent() { return customAccent; }
    public String getCustomText() { return customText; }

    public void setTheme(Theme newTheme) {
        this.theme = newTheme;
        save();
    }

    public void setCustomColors(String bg, String accent, String text) {
        this.customBg = bg;
        this.customAccent = accent;
        this.customText = text;
        writeCustomCssFile();
        save();
    }

    public List<String> getStylesheets() {
        List<String> list = new ArrayList<>();
        list.add(resource("/css/styles.css"));

        if (theme == Theme.CUSTOM) {
            // додаємо мітку часу до URI, щоб JavaFX не використав закешовану
            // попередню версію файлу після перезапису кольорів
            list.add(customCssFile.toUri().toString() + "?t=" + System.currentTimeMillis());
        } else {
            list.add(resource(theme.resourcePath));
        }
        return list;
    }

    /** Застосувати поточну тему до вже створеної сцени (для живого перемикання без перезапуску вікна). */
    public void applyTo(Scene scene) {
        scene.getStylesheets().setAll(getStylesheets());
    }

    private String resource(String path) {
        return getClass().getResource(path).toExternalForm();
    }

    /**
     * Генерує .root-блок кастомної теми на диску: три обрані кольори (фон, акцент, текст)
     * плюс похідні відтінки через derive(), щоб не змушувати користувача підбирати
     * всі 13 змінних вручну.
     */
    private void writeCustomCssFile() {
        try {
            Files.createDirectories(customCssFile.getParent());
        } catch (IOException e) {
            return;
        }

        boolean darkBg = isDark(customBg);
        String secondaryBg = darkBg ? deriveHex(customBg, 8) : deriveHex(customBg, -3);
        String cardBorder = darkBg ? deriveHex(customBg, 15) : deriveHex(customBg, -8);
        String secondaryText = darkBg ? deriveHex(customText, -35) : deriveHex(customText, 35);
        String accentHover = deriveHex(customAccent, darkBg ? 12 : -12);
        String selectionBg = darkBg ? deriveHex(customAccent, -55) : deriveHex(customAccent, 80);
        String buttonPrimaryBg = darkBg ? customText : customText.equals(DEFAULT_CUSTOM_TEXT) ? "#0F1419" : deriveHex(customBg, -70);
        String buttonPrimaryText = darkBg ? customBg : "#FFFFFF";
        String buttonSecondaryBg = darkBg ? deriveHex(customBg, 10) : deriveHex(customBg, -4);
        String buttonSecondaryHoverBg = darkBg ? deriveHex(customBg, 16) : deriveHex(customBg, -9);
        String buttonPrimaryHoverBg = deriveHex(buttonPrimaryBg, darkBg ? 12 : -12);

        String css = """
        .root {
            -fx-primary-bg: %s;
            -fx-secondary-bg: %s;
            -fx-card-border: %s;
            -fx-primary-text: %s;
            -fx-secondary-text: %s;
            -fx-accent: %s;
            -fx-accent-hover: %s;
            -fx-button-primary-bg: %s;
            -fx-button-primary-text: %s;
            -fx-button-primary-hover-bg: %s;
            -fx-button-secondary-bg: %s;
            -fx-button-secondary-hover-bg: %s;
            -fx-input-bg: %s;
            -fx-input-border: %s;
            -fx-bubble-theirs-bg: %s;
            -fx-error-text: #D93025;
            -fx-like-color: #F91880;
            -fx-save-color: %s;
            -fx-share-color: #00BA7C;
            -fx-selection-bg: %s;
        }
        """.formatted(customBg, secondaryBg, cardBorder, customText, secondaryText,
                customAccent, accentHover, buttonPrimaryBg, buttonPrimaryText, buttonPrimaryHoverBg,
                buttonSecondaryBg, buttonSecondaryHoverBg, secondaryBg, cardBorder, buttonSecondaryBg,
                customAccent, selectionBg);
        try (Writer writer = Files.newBufferedWriter(customCssFile, StandardCharsets.UTF_8)) {
            writer.write(css);
        } catch (IOException ignored) {
            // не критично: тема просто не збережеться між запусками
        }
    }

    private static boolean isDark(String hex) {
        Color c = Color.web(hex);
        double luminance = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
        return luminance < 0.5;
    }

    private static String deriveHex(String hex, int percent) {
        Color c = Color.web(hex);
        double factor = percent / 100.0;
        Color derived;
        if (factor >= 0) {
            derived = c.interpolate(Color.WHITE, factor);
        } else {
            derived = c.interpolate(Color.BLACK, -factor);
        }
        return toHex(derived);
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    private void load() {
        if (!Files.exists(themePropsFile)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(themePropsFile)) {
            props.load(in);
            String themeName = props.getProperty(KEY_THEME, Theme.LIGHT.name());
            try {
                theme = Theme.valueOf(themeName);
            } catch (IllegalArgumentException ignored) {
                theme = Theme.LIGHT;
            }
            customBg = props.getProperty(KEY_CUSTOM_BG, DEFAULT_CUSTOM_BG);
            customAccent = props.getProperty(KEY_CUSTOM_ACCENT, DEFAULT_CUSTOM_ACCENT);
            customText = props.getProperty(KEY_CUSTOM_TEXT, DEFAULT_CUSTOM_TEXT);
        } catch (IOException ignored) {
            // лишаємось на значеннях за замовчуванням
        }
    }

    private void save() {
        try {
            Files.createDirectories(themePropsFile.getParent());
        } catch (IOException e) {
            return;
        }

        Properties props = new Properties();
        props.setProperty(KEY_THEME, theme.name());
        props.setProperty(KEY_CUSTOM_BG, customBg);
        props.setProperty(KEY_CUSTOM_ACCENT, customAccent);
        props.setProperty(KEY_CUSTOM_TEXT, customText);

        try (OutputStream out = Files.newOutputStream(themePropsFile)) {
            props.store(out, "Social Network client theme");
        } catch (IOException ignored) {
            // не критично
        }
    }
}