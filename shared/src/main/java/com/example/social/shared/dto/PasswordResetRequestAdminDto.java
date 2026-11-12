package com.example.social.shared.dto;

import java.time.LocalDateTime;

/**
 * Заявка «Забув пароль» для адмін-панелі. email і phone — дані з акаунта,
 * щоб адміністратор міг звірити їх із контактом, який вказав автор заявки.
 *
 * @param status pending / resolved / rejected
 */
public record PasswordResetRequestAdminDto(long requestId, long userId, String username,
                                           String email, String phone,
                                           String contact, String message, String status,
                                           LocalDateTime createdAt, LocalDateTime resolvedAt,
                                           String resolvedBy) {
}