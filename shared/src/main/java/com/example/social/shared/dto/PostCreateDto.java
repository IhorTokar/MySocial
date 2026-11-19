package com.example.social.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Межі відповідають колонкам БД (label, media_url — VARCHAR(255), tags.name — VARCHAR(50)). */
public class PostCreateDto {

    @Size(max = 255, message = "заголовок — щонайбільше 255 символів")
    private String label;

    @NotBlank(message = "текст поста не може бути порожнім")
    @Size(max = 5000, message = "текст поста — щонайбільше 5000 символів")
    private String text;

    @Size(max = 255, message = "посилання на медіа — щонайбільше 255 символів")
    private String mediaUrl;

    @Size(max = 10, message = "щонайбільше 10 тегів")
    private List<@NotBlank(message = "тег не може бути порожнім")
    @Size(max = 50, message = "тег — щонайбільше 50 символів") String> tags;

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