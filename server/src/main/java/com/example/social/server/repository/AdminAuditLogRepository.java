package com.example.social.server.repository;

import com.example.social.server.entity.AdminAuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    List<AdminAuditLog> findAllByOrderByAuditIdDesc(Pageable pageable);
}