package com.example.social.shared.dto;

import java.time.LocalDateTime;

public class ConversationDto {

    private Long userId;
    private String username;
    private String displayName;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private long unreadCount;

    public ConversationDto(Long userId, String username, String displayName,
                           String lastMessage, LocalDateTime lastMessageAt, long unreadCount) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.lastMessage = lastMessage;
        this.lastMessageAt = lastMessageAt;
        this.unreadCount = unreadCount;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getLastMessage() { return lastMessage; }
    public LocalDateTime getLastMessageAt() { return lastMessageAt; }
    public long getUnreadCount() { return unreadCount; }
}