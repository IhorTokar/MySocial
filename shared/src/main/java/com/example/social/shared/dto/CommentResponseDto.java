package com.example.social.shared.dto;

import java.time.LocalDateTime;

public class CommentResponseDto {

    private Long commentId;
    private String authorUsername;
    private String text;
    private LocalDateTime createdAt;
    private Long parentCommentId;

    public CommentResponseDto(Long commentId, String authorUsername, String text,
                              LocalDateTime createdAt, Long parentCommentId) {
        this.commentId = commentId;
        this.authorUsername = authorUsername;
        this.text = text;
        this.createdAt = createdAt;
        this.parentCommentId = parentCommentId;
    }

    public Long getCommentId() {
        return commentId;
    }

    public String getAuthorUsername() {
        return authorUsername;
    }

    public String getText() {
        return text;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getParentCommentId() {
        return parentCommentId;
    }
}