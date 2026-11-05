package com.example.social.shared.dto;

import java.time.LocalDateTime;

public record AuditLogEntryDto(long id, Long adminId, String adminUsername, String action,
                               String targetType, Long targetId, String details,
                               LocalDateTime createdAt) {
}