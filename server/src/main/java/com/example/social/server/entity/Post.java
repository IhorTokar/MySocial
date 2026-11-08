package com.example.social.server.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 255)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(name = "media_url")
    private String mediaUrl;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate = LocalDateTime.now();

    @Column(name = "update_date")
    private LocalDateTime updateDate;

    /** Тональність тексту в [-1, 1]; null — ще не обчислено. */
    @Column
    private Double sentiment;

    /** Звідки взято тональність: model / lexicon / empty (див. V7__post_sentiment.sql). */
    @Column(name = "sentiment_source", length = 10)
    private String sentimentSource;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "post_tags",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    // ===== Геттери / сеттери =====

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

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

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public LocalDateTime getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(LocalDateTime updateDate) {
        this.updateDate = updateDate;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    public Double getSentiment() {
        return sentiment;
    }

    public String getSentimentSource() {
        return sentimentSource;
    }

    public void setSentiment(Double sentiment, String source) {
        this.sentiment = sentiment;
        this.sentimentSource = source;
    }
}