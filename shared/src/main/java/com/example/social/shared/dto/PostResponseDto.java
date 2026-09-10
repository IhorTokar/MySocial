package com.example.social.shared.dto;

import java.time.LocalDateTime;
import java.util.List;

public class PostResponseDto {

    private Long postId;
    private String authorUsername;
    private String label;
    private String text;
    private String mediaUrl;
    private LocalDateTime createdDate;
    private List<String> tags;

    public PostResponseDto(Long postId, String authorUsername, String label,
                           String text, String mediaUrl, LocalDateTime createdDate,
                           List<String> tags) {
        this.postId = postId;
        this.authorUsername = authorUsername;
        this.label = label;
        this.text = text;
        this.mediaUrl = mediaUrl;
        this.createdDate = createdDate;
        this.tags = tags;
    }

    public Long getPostId() {
        return postId;
    }

    public String getAuthorUsername() {
        return authorUsername;
    }

    public String getLabel() {
        return label;
    }

    public String getText() {
        return text;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public List<String> getTags() {
        return tags;
    }
}