package com.example.social.shared.dto;

/** Відповідь на POST /api/auth/register. */
public record RegisterResponseDto(long userId, String username, String uid) {}