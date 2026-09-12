package com.example.social.shared.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class PostCreateDto {

    private String label;

    @NotBlank
    private String text;

    private String mediaUrl;

    private List<String> tags;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}