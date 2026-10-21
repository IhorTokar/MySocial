package com.example.social.server.security;

import com.example.social.server.entity.UserRole;

public class AuthenticatedUser {

    private final Long userId;
    private final String username;
    private final UserRole role;

    public AuthenticatedUser(Long userId, String username, UserRole role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == UserRole.admin;
    }
}
