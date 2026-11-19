package com.example.social.client.util;

public class SessionManager {

    private static SessionManager instance;

    private String token;
    private Long userId;
    private String username;
    private String role;
    private volatile boolean tokenRefreshing;

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

    /** Новий токен після зміни пароля: решта сесії (користувач, роль) не змінюється. */
    public void updateToken(String token) {
        this.token = token;
    }

    /**
     * Поки йде зміна пароля, інші запити зі старим токеном можуть отримати 401 —
     * це не кінець сесії, а заміна токена, тож вихід на екран входу пропускається.
     */
    public void setTokenRefreshing(boolean refreshing) {
        this.tokenRefreshing = refreshing;
    }

    public boolean isTokenRefreshing() {
        return tokenRefreshing;
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