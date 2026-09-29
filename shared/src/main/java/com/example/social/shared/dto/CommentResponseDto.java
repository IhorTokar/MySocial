package com.example.social.shared.dto;

import java.time.LocalDateTime;

public class CommentResponseDto {

    private Long commentId;
    private Long authorId;
    private String authorUsername;
    private String authorDisplayName;
    private String authorAvatarUrl;
    private String text;
    private LocalDateTime createdAt;
    private Long parentCommentId;

    public CommentResponseDto(Long commentId, Long authorId, String authorUsername, String authorDisplayName,
                              String authorAvatarUrl, String text, LocalDateTime createdAt, Long parentCommentId) {
        this.commentId = commentId;
        this.authorId = authorId;
        this.authorUsername = authorUsername;
        this.authorDisplayName = authorDisplayName;
        this.authorAvatarUrl = authorAvatarUrl;
        this.text = text;
        this.createdAt = createdAt;
        this.parentCommentId = parentCommentId;
    }

    public Long getCommentId() { return commentId; }
    public Long getAuthorId() { return authorId; }
    public String getAuthorUsername() { return authorUsername; }
    public String getAuthorDisplayName() { return authorDisplayName; }
    public String getAuthorAvatarUrl() { return authorAvatarUrl; }
    public String getText() { return text; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getParentCommentId() { return parentCommentId; }
}