package com.example.social.shared.dto;

public class UserSummaryDto {

    private Long userId;
    private String username;
    private String displayName;
    private String avatarUrl;
    private boolean followedByCurrentUser;

    public UserSummaryDto(Long userId, String username, String displayName,
                          String avatarUrl, boolean followedByCurrentUser) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.followedByCurrentUser = followedByCurrentUser;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public boolean isFollowedByCurrentUser() { return followedByCurrentUser; }
}