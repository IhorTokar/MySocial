package com.example.social.shared.dto;

import java.time.LocalDateTime;

public class MessageDto {

    private Long messageId;
    private Long senderId;
    private Long receiverId;
    private String text;
    private String photoUrl;
    private LocalDateTime createdAt;
    private boolean read;
    private boolean edited;
    private boolean deleted;
    private Long parentMessageId;
    private String parentSnippet;
    private String parentSenderUsername;

    public MessageDto(Long messageId, Long senderId, Long receiverId, String text, String photoUrl,
                      LocalDateTime createdAt, boolean read, boolean edited, boolean deleted,
                      Long parentMessageId, String parentSnippet, String parentSenderUsername) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.text = text;
        this.photoUrl = photoUrl;
        this.createdAt = createdAt;
        this.read = read;
        this.edited = edited;
        this.deleted = deleted;
        this.parentMessageId = parentMessageId;
        this.parentSnippet = parentSnippet;
        this.parentSenderUsername = parentSenderUsername;
    }

    public Long getMessageId() { return messageId; }
    public Long getSenderId() { return senderId; }
    public Long getReceiverId() { return receiverId; }
    public String getText() { return text; }
    public String getPhotoUrl() { return photoUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isRead() { return read; }
    public boolean isEdited() { return edited; }
    public boolean isDeleted() { return deleted; }
    public Long getParentMessageId() { return parentMessageId; }
    public String getParentSnippet() { return parentSnippet; }
    public String getParentSenderUsername() { return parentSenderUsername; }
}