package com.example.social.client.util;

public class SessionManager {

    private static SessionManager instance;

    private String token;
    private Long userId;
    private String username;

    private SessionManager() {
    }

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public void setSession(String token, Long userId, String username) {
        this.token = token;
        this.userId = userId;
        this.username = username;
    }

    public void clear() {
        this.token = null;
        this.userId = null;
        this.username = null;
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
}