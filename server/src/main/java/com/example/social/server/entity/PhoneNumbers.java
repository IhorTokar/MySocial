package com.example.social.server.service;

import com.example.social.server.exception.BadRequestException;

/**
 * Приведення номера телефону до єдиного міжнародного вигляду (+380671234567),
 * щоб «067 123-45-67» і «+38 (067) 123 45 67» вважались одним номером.
 */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /** @return номер у форматі +XXXXXXXXXXX або null, якщо поле порожнє */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.replaceAll("[\\s()\\-.]", "");
        if (value.startsWith("00")) {
            value = "+" + value.substring(2);          // 00380… → +380…
        } else if (value.matches("0\\d{9}")) {
            value = "+38" + value;                     // український формат 0XX XXX XX XX
        } else if (value.matches("380\\d{9}")) {
            value = "+" + value;                       // 380… без плюса
        }
        if (!value.matches("\\+\\d{10,15}")) {
            throw new BadRequestException("Невірний формат номера. Приклад: +380 67 123 45 67");
        }
        return value;
    }
}