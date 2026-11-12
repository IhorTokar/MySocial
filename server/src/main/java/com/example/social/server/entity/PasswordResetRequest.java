package com.example.social.server.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Заявка «Забув пароль», яку розглядає адміністратор. */
@Entity
@Table(name = "password_reset_request")
public class PasswordResetRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 200)
    private String contact;

    @Column(length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PasswordResetStatus status = PasswordResetStatus.pending;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    protected PasswordResetRequest() {
    }

    public PasswordResetRequest(User user, String contact, String message) {
        this.user = user;
        this.contact = contact;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    /** Закрити заявку: resolved — пароль скинуто, rejected — відхилено. */
    public void close(PasswordResetStatus result, User admin) {
        this.status = result;
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = admin;
    }

    public Long getRequestId() { return requestId; }
    public User getUser() { return user; }
    public String getContact() { return contact; }
    public String getMessage() { return message; }
    public PasswordResetStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public User getResolvedBy() { return resolvedBy; }
}