package com.example.social.server.service;

import com.example.social.server.entity.AuditAction;
import com.example.social.server.entity.PasswordResetRequest;
import com.example.social.server.entity.PasswordResetStatus;
import com.example.social.server.entity.User;
import com.example.social.server.entity.UserPrivate;
import com.example.social.server.exception.ConflictException;
import com.example.social.server.exception.NotFoundException;
import com.example.social.server.repository.PasswordResetRequestRepository;
import com.example.social.server.repository.UserPrivateRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.shared.dto.PasswordResetRequestAdminDto;
import com.example.social.shared.dto.PasswordResetRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * «Забув пароль» без поштового сервера: користувач залишає заявку, адміністратор звіряє контакт
 * із даними акаунта, встановлює тимчасовий пароль і повідомляє його користувачу.
 */
@Service
public class PasswordResetRequestService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetRequestService.class);

    private final PasswordResetRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final UserPrivateRepository userPrivateRepository;
    private final AdminUserService adminUserService;
    private final AuditService auditService;

    public PasswordResetRequestService(PasswordResetRequestRepository requestRepository,
                                       UserRepository userRepository,
                                       UserPrivateRepository userPrivateRepository,
                                       AdminUserService adminUserService,
                                       AuditService auditService) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.userPrivateRepository = userPrivateRepository;
        this.adminUserService = adminUserService;
        this.auditService = auditService;
    }

    /**
     * Прийняти заявку. Відповідь для автора однакова за будь-якого результату — щоб за нею
     * не можна було з'ясувати, чи існує акаунт з таким логіном або email.
     */
    @Transactional
    public void submit(PasswordResetRequestDto dto) {
        Optional<User> user = findUser(dto.login().trim());
        if (user.isEmpty()) {
            log.info("Заявка на пароль для неіснуючого акаунта «{}» — пропущено", dto.login());
            return;
        }
        if (requestRepository.existsByUserAndStatus(user.get(), PasswordResetStatus.pending)) {
            log.info("Заявка на пароль для user {} уже відкрита — повтор пропущено", user.get().getUserId());
            return;
        }
        requestRepository.save(new PasswordResetRequest(user.get(), blankToNull(dto.contact()), blankToNull(dto.message())));
        log.info("Нова заявка на пароль від user {}", user.get().getUserId());
    }

    @Transactional(readOnly = true)
    public List<PasswordResetRequestAdminDto> list(boolean onlyPending) {
        List<PasswordResetRequest> requests = onlyPending
                ? requestRepository.findByStatusOrderByCreatedAtDesc(PasswordResetStatus.pending)
                : requestRepository.findTop200ByOrderByCreatedAtDesc();
        return requests.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return requestRepository.countByStatus(PasswordResetStatus.pending);
    }

    /** Встановити тимчасовий пароль (усі сесії користувача завершуються) і закрити заявку. */
    @Transactional
    public PasswordResetRequestAdminDto resolve(long requestId, String newPassword, long adminId) {
        PasswordResetRequest request = pendingRequest(requestId);
        adminUserService.resetPassword(request.getUser().getUserId(), newPassword, adminId);
        request.close(PasswordResetStatus.resolved, userRepository.getReferenceById(adminId));
        return toDto(request);
    }

    @Transactional
    public PasswordResetRequestAdminDto reject(long requestId, long adminId) {
        PasswordResetRequest request = pendingRequest(requestId);
        request.close(PasswordResetStatus.rejected, userRepository.getReferenceById(adminId));
        auditService.log(adminId, AuditAction.PASSWORD_REQUEST_REJECT, "USER", request.getUser().getUserId(),
                "заявку #" + requestId + " відхилено");
        return toDto(request);
    }

    // ===== Допоміжне =====

    /** Спершу за ім'ям користувача, потім за email. */
    private Optional<User> findUser(String login) {
        Optional<User> byUsername = userRepository.findByUsername(login);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        return userPrivateRepository.findByEmail(login).map(UserPrivate::getUser);
    }

    private PasswordResetRequest pendingRequest(long requestId) {
        PasswordResetRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Заявку #" + requestId + " не знайдено"));
        if (request.getStatus() != PasswordResetStatus.pending) {
            throw new ConflictException("Заявку #" + requestId + " уже розглянуто");
        }
        return request;
    }

    private PasswordResetRequestAdminDto toDto(PasswordResetRequest r) {
        User user = r.getUser();
        UserPrivate p = userPrivateRepository.findByUser(user).orElse(null);
        return new PasswordResetRequestAdminDto(
                r.getRequestId(), user.getUserId(), user.getUsername(),
                p == null ? null : p.getEmail(), p == null ? null : p.getPhoneNum(),
                r.getContact(), r.getMessage(), r.getStatus().name(),
                r.getCreatedAt(), r.getResolvedAt(),
                r.getResolvedBy() == null ? null : r.getResolvedBy().getUsername());
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}