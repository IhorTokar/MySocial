package com.example.social.shared.dto;

import java.time.LocalDateTime;

/** Повні дані користувача для адмін-панелі (включно з приватними полями). */
public record AdminUserDto(long userId, String uid, String username, String displayName,
                           String aboutMe, String avatarUrl, String email, String phone, String role,
                           boolean blocked, String blockedReason, LocalDateTime blockedAt,
                           LocalDateTime createdAt,
                           long postsCount, long followersCount, long followingCount) {
}