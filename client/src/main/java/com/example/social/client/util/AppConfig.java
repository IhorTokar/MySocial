package com.example.social.client.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Налаштування з resources/app.properties: назва застосунку та підпис. */
public final class AppConfig {

    private static final Properties PROPS = load();

    private AppConfig() {
    }

    /** Назва — логотип у шапці й заголовки вікон. */
    public static String name() {
        return PROPS.getProperty("app.name", "NexusConnect");
    }

    public static String tagline() {
        return PROPS.getProperty("app.tagline", "");
    }

    /** «NexusConnect — Реєстрація» тощо. */
    public static String title(String page) {
        return page == null || page.isBlank() ? name() : name() + " — " + page;
    }

    private static Properties load() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream("/app.properties")) {
            if (in != null) {
                // UTF-8, щоб кирилиця в підписі читалась правильно (Properties за замовчуванням — ISO-8859-1)
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // лишаються значення за замовчуванням
        }
        return props;
    }
}