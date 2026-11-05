package com.example.social.server.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_private")
public class UserPrivate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "private_id")
    private Long privateId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "phone_num", unique = true, length = 50)
    private String phoneNum;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "role", nullable = false)
    private UserRole role = UserRole.user;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private boolean blocked = false;

    @Column(name = "blocked_reason", length = 255)
    private String blockedReason;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    // ===== Геттери / сеттери =====

    public Long getPrivateId() {
        return privateId;
    }

    public void setPrivateId(Long privateId) {
        this.privateId = privateId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNum() {
        return phoneNum;
    }

    public void setPhoneNum(String phoneNum) {
        this.phoneNum = phoneNum;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public void setBlockedReason(String blockedReason) {
        this.blockedReason = blockedReason;
    }

    public LocalDateTime getBlockedAt() {
        return blockedAt;
    }

    public void setBlockedAt(LocalDateTime blockedAt) {
        this.blockedAt = blockedAt;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    /** Робить недійсними всі раніше видані JWT цього користувача. */
    public void incrementTokenVersion() {
        this.tokenVersion++;
    }
}