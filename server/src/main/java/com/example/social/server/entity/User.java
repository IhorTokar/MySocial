package com.example.social.server.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "\"user\"")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "uid", unique = true, nullable = false, length = 10)
    private String uid;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "user_avatar_url")
    private String userAvatarUrl;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @Column(name = "about_me", columnDefinition = "TEXT")
    private String aboutMe;

    @Column(name = "last_logout")
    private LocalDateTime lastLogout;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ===== Геттери / сеттери =====

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getUserAvatarUrl() {
        return userAvatarUrl;
    }

    public void setUserAvatarUrl(String userAvatarUrl) {
        this.userAvatarUrl = userAvatarUrl;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    public String getAboutMe() {
        return aboutMe;
    }

    public void setAboutMe(String aboutMe) {
        this.aboutMe = aboutMe;
    }

    public LocalDateTime getLastLogout() {
        return lastLogout;
    }

    public void setLastLogout(LocalDateTime lastLogout) {
        this.lastLogout = lastLogout;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}