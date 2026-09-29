package com.example.social.shared.dto;

import java.time.LocalDateTime;
import java.util.List;

public class PostResponseDto {

    private Long postId;
    private Long authorId;
    private String authorUsername;
    private String authorDisplayName;
    private String authorAvatarUrl;
    private String label;
    private String text;
    private String mediaUrl;
    private LocalDateTime createdDate;
    private List<String> tags;
    private long likesCount;
    private long commentsCount;
    private boolean likedByCurrentUser;
    private boolean savedByCurrentUser;

    public PostResponseDto(Long postId, Long authorId, String authorUsername, String authorDisplayName,
                           String authorAvatarUrl, String label, String text, String mediaUrl,
                           LocalDateTime createdDate, List<String> tags,
                           long likesCount, long commentsCount,
                           boolean likedByCurrentUser, boolean savedByCurrentUser) {
        this.postId = postId;
        this.authorId = authorId;
        this.authorUsername = authorUsername;
        this.authorDisplayName = authorDisplayName;
        this.authorAvatarUrl = authorAvatarUrl;
        this.label = label;
        this.text = text;
        this.mediaUrl = mediaUrl;
        this.createdDate = createdDate;
        this.tags = tags;
        this.likesCount = likesCount;
        this.commentsCount = commentsCount;
        this.likedByCurrentUser = likedByCurrentUser;
        this.savedByCurrentUser = savedByCurrentUser;
    }

    public Long getPostId() { return postId; }
    public Long getAuthorId() { return authorId; }
    public String getAuthorUsername() { return authorUsername; }
    public String getAuthorDisplayName() { return authorDisplayName; }
    public String getAuthorAvatarUrl() { return authorAvatarUrl; }
    public String getLabel() { return label; }
    public String getText() { return text; }
    public String getMediaUrl() { return mediaUrl; }
    public LocalDateTime getCreatedDate() { return createdDate; }
    public List<String> getTags() { return tags; }
    public long getLikesCount() { return likesCount; }
    public long getCommentsCount() { return commentsCount; }
    public boolean isLikedByCurrentUser() { return likedByCurrentUser; }
    public boolean isSavedByCurrentUser() { return savedByCurrentUser; }
}