package com.example.social.shared.dto;

import java.time.LocalDateTime;

public class MessageDto {

    private Long messageId;
    private Long senderId;
    private Long receiverId;
    private String text;
    private LocalDateTime createdAt;
    private boolean read;

    public MessageDto(Long messageId, Long senderId, Long receiverId, String text,
                      LocalDateTime createdAt, boolean read) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.text = text;
        this.createdAt = createdAt;
        this.read = read;
    }

    public Long getMessageId() { return messageId; }
    public Long getSenderId() { return senderId; }
    public Long getReceiverId() { return receiverId; }
    public String getText() { return text; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isRead() { return read; }
}