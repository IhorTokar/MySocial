package com.example.social.client.util;

public class SessionManager {

    private static SessionManager instance;

    private String token;
    private Long userId;
    private String username;
    private String role;

    private SessionManager() {
    }

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public void setSession(String token, Long userId, String username, String role) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public void clear() {
        this.token = null;
        this.userId = null;
        this.username = null;
        this.role = null;
    }

    public boolean isLoggedIn() {
        return token != null;
    }

    public String getToken() {
        return token;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    /** Чи бачить користувач адмін-функції. Остаточну перевірку все одно робить сервер. */
    public boolean isAdmin() {
        return "admin".equals(role);
    }
}