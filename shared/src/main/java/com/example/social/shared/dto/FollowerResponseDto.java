package com.example.social.shared.dto;

import java.time.LocalDateTime;

public class FollowerResponseDto {

    private Long userId;
    private String username;
    private String displayName;
    private LocalDateTime followedSince;

    public FollowerResponseDto(Long userId, String username, String displayName, LocalDateTime followedSince) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.followedSince = followedSince;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LocalDateTime getFollowedSince() {
        return followedSince;
    }
}