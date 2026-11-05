package com.example.social.shared.dto;

/** Відповідь на POST /api/auth/login. */
public record LoginResponseDto(String token, long userId, String username, String role) {}