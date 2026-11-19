package com.example.social.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CommentCreateDto {

    @NotBlank(message = "коментар не може бути порожнім")
    @Size(max = 2000, message = "коментар — щонайбільше 2000 символів")
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