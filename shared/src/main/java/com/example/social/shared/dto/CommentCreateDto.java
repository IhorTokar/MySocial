package com.example.social.shared.dto;

import jakarta.validation.constraints.NotBlank;

public class CommentCreateDto {

    @NotBlank
    private String text;

    private Long parentCommentId;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Long getParentCommentId() {
        return parentCommentId;
    }

    public void setParentCommentId(Long parentCommentId) {
        this.parentCommentId = parentCommentId;
    }
}