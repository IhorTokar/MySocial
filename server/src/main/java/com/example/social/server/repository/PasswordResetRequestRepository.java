package com.example.social.server.repository;

import com.example.social.server.entity.PasswordResetRequest;
import com.example.social.server.entity.PasswordResetStatus;
import com.example.social.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, Long> {

    boolean existsByUserAndStatus(User user, PasswordResetStatus status);

    List<PasswordResetRequest> findByStatusOrderByCreatedAtDesc(PasswordResetStatus status);

    List<PasswordResetRequest> findTop200ByOrderByCreatedAtDesc();

    long countByStatus(PasswordResetStatus status);
}