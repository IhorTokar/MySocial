package com.example.social.client.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** Форматування значень для екранів адмін-панелі. */
public final class AdminFormat {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private static final Map<String, String> ACTIONS = Map.of(
            "WEIGHTS_UPDATE", "Зміна ваг",
            "WEIGHTS_RESTORE", "Відновлення ваг",
            "USER_BLOCK", "Блокування",
            "USER_UNBLOCK", "Розблокування",
            "USER_ROLE_CHANGE", "Зміна ролі",
            "USER_PROFILE_EDIT", "Редагування профілю",
            "USER_PASSWORD_RESET", "Скидання пароля",
            "POST_DELETE", "Видалення поста",
            "COMMENT_DELETE", "Видалення коментаря");

    private static final Map<String, String> TARGETS = Map.of(
            "WEIGHTS", "ваги",
            "USER", "користувач",
            "POST", "пост",
            "COMMENT", "коментар");

    private AdminFormat() {
    }

    public static String dateTime(LocalDateTime value) {
        return value == null ? "—" : value.format(DATE_TIME);
    }

    public static String weight(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
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