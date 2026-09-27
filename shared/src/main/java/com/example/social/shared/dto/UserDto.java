package com.example.social.shared.dto;

public class UserDto {

    private Long userId;
    private String uid;
    private String username;
    private String displayName;
    private String aboutMe;
    private String userAvatarUrl;
    private long postsCount;
    private long followersCount;
    private long followingCount;
    private boolean followedByCurrentUser;

    public UserDto(Long userId, String uid, String username, String displayName, String aboutMe,
                   String userAvatarUrl, long postsCount, long followersCount,
                   long followingCount, boolean followedByCurrentUser) {
        this.userId = userId;
        this.uid = uid;
        this.username = username;
        this.displayName = displayName;
        this.aboutMe = aboutMe;
        this.userAvatarUrl = userAvatarUrl;
        this.postsCount = postsCount;
        this.followersCount = followersCount;
        this.followingCount = followingCount;
        this.followedByCurrentUser = followedByCurrentUser;
    }

    public Long getUserId() { return userId; }
    public String getUid() { return uid; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getAboutMe() { return aboutMe; }
    public String getUserAvatarUrl() { return userAvatarUrl; }
    public long getPostsCount() { return postsCount; }
    public long getFollowersCount() { return followersCount; }
    public long getFollowingCount() { return followingCount; }
    public boolean isFollowedByCurrentUser() { return followedByCurrentUser; }
}