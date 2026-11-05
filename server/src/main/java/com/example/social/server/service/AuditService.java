package com.example.social.server.service;

import com.example.social.server.entity.AdminAuditLog;
import com.example.social.server.entity.AuditAction;
import com.example.social.server.entity.User;
import com.example.social.server.repository.AdminAuditLogRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.shared.dto.AuditLogEntryDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Журнал дій адміністраторів. */
@Service
public class AuditService {

    private final AdminAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditService(AdminAuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    /** Записує дію. Виконується в транзакції того, хто викликає: якщо дія відкотиться, запис теж. */
    @Transactional
    public void log(Long adminId, AuditAction action, String targetType, Long targetId, String details) {
        User admin = adminId == null ? null : userRepository.getReferenceById(adminId);
        auditLogRepository.save(new AdminAuditLog(admin, action, targetType, targetId, details));
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntryDto> recent(int limit) {
        return auditLogRepository.findAllByOrderByAuditIdDesc(PageRequest.of(0, limit)).stream()
                .map(e -> new AuditLogEntryDto(
                        e.getAuditId(),
                        e.getAdmin() == null ? null : e.getAdmin().getUserId(),
                        e.getAdmin() == null ? null : e.getAdmin().getUsername(),
                        e.getAction().name(),
                        e.getTargetType(),
                        e.getTargetId(),
                        e.getDetails(),
                        e.getCreatedAt()))
                .toList();
    }
}