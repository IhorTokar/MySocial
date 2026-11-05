package com.example.social.server.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Запис журналу дій адміністратора. */
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private User admin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Column(name = "target_type", length = 20)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected AdminAuditLog() {
    }

    public AdminAuditLog(User admin, AuditAction action, String targetType, Long targetId, String details) {
        this.admin = admin;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.details = details;
    }

    public Long getAuditId() { return auditId; }
    public User getAdmin() { return admin; }
    public AuditAction getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}