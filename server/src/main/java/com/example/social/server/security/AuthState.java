package com.example.social.server.security;

import com.example.social.server.entity.UserRole;

/** Те, що потрібно знати про користувача при кожному запиті: роль, блокування, версія токенів. */
public record AuthState(UserRole role, boolean blocked, int tokenVersion) {

    /** Чи можна пустити користувача з токеном цієї версії. */
    public boolean allows(int tokenVersionInToken) {
        return !blocked && tokenVersion == tokenVersionInToken;
    }
}