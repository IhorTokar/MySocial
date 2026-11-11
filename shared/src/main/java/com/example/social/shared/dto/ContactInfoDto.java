package com.example.social.shared.dto;

/** Контактні дані власника акаунта (бачать лише він сам і адміністратор). phone == null — номер не вказано. */
public record ContactInfoDto(String email, String phone) {
}