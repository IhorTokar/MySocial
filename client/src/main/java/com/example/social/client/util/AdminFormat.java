package com.example.social.client.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** Форматування значень для екранів адмін-панелі. */
public final class AdminFormat {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    // Map.ofEntries, бо Map.of приймає не більше 10 пар
    private static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry("WEIGHTS_UPDATE", "Зміна ваг"),
            Map.entry("WEIGHTS_RESTORE", "Відновлення ваг"),
            Map.entry("USER_BLOCK", "Блокування"),
            Map.entry("USER_UNBLOCK", "Розблокування"),
            Map.entry("USER_ROLE_CHANGE", "Зміна ролі"),
            Map.entry("USER_PROFILE_EDIT", "Редагування профілю"),
            Map.entry("USER_PASSWORD_RESET", "Скидання пароля"),
            Map.entry("POST_DELETE", "Видалення поста"),
            Map.entry("COMMENT_DELETE", "Видалення коментаря"),
            Map.entry("ECHO_SNAPSHOT", "Знімок ехо-камер"),
            Map.entry("PASSWORD_REQUEST_REJECT", "Відхилення заявки на пароль"));

    private static final Map<String, String> REQUEST_STATUSES = Map.of(
            "pending", "нова",
            "resolved", "пароль скинуто",
            "rejected", "відхилено");

    private static final Map<String, String> TARGETS = Map.of(
            "WEIGHTS", "ваги",
            "USER", "користувач",
            "POST", "пост",
            "COMMENT", "коментар",
            "echo_snapshot", "знімок");

    private AdminFormat() {
    }

    public static String dateTime(LocalDateTime value) {
        return value == null ? "—" : value.format(DATE_TIME);
    }

    public static String weight(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    /** Echo-score та інші частки 0..1; null — «—». */
    public static String score(Double value) {
        return value == null ? "—" : String.format(Locale.ROOT, "%.3f", value);
    }

    /** Зміна зі знаком: +0.012 / −0.004; null — «—». */
    public static String delta(Double value) {
        if (value == null) {
            return "—";
        }
        if (Math.abs(value) < 0.0005) {
            return "0.000";
        }
        return (value > 0 ? "+" : "−") + String.format(Locale.ROOT, "%.3f", Math.abs(value));
    }

    public static String percent(Double value) {
        return value == null ? "—" : String.format(Locale.ROOT, "%.0f%%", value * 100);
    }

    /** Стан заявки «Забув пароль». */
    public static String requestStatus(String code) {
        return REQUEST_STATUSES.getOrDefault(code, code);
    }

    public static String action(String code) {
        return ACTIONS.getOrDefault(code, code);
    }

    public static String target(String type, Long id) {
        if (type == null) {
            return "—";
        }
        String name = TARGETS.getOrDefault(type, type.toLowerCase(Locale.ROOT));
        return id == null ? name : name + " #" + id;
    }

    public static String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}