package com.example.social.server.service;

import com.example.social.server.entity.AuditAction;
import com.example.social.server.entity.User;
import com.example.social.server.entity.UserPrivate;
import com.example.social.server.entity.UserRole;
import com.example.social.server.exception.BadRequestException;
import com.example.social.server.exception.ConflictException;
import com.example.social.server.exception.NotFoundException;
import com.example.social.server.repository.FollowersRepository;
import com.example.social.server.repository.PostRepository;
import com.example.social.server.repository.UserPrivateRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.server.websocket.ChatSessionRegistry;
import com.example.social.shared.dto.AdminProfileUpdateDto;
import com.example.social.shared.dto.AdminUserDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Керування користувачами з адмін-панелі. Кожна зміна записується в журнал. */
@Service
public class AdminUserService {

    private static final String TARGET_USER = "USER";

    private final UserRepository userRepository;
    private final UserPrivateRepository userPrivateRepository;
    private final PostRepository postRepository;
    private final FollowersRepository followersRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final ChatSessionRegistry chatSessionRegistry;

    public AdminUserService(UserRepository userRepository,
                            UserPrivateRepository userPrivateRepository,
                            PostRepository postRepository,
                            FollowersRepository followersRepository,
                            PasswordEncoder passwordEncoder,
                            AuditService auditService,
                            ChatSessionRegistry chatSessionRegistry) {
        this.userRepository = userRepository;
        this.userPrivateRepository = userPrivateRepository;
        this.postRepository = postRepository;
        this.followersRepository = followersRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.chatSessionRegistry = chatSessionRegistry;
    }

    /** Пошук за числовим ID, за uid або за частиною username / displayName. */
    @Transactional(readOnly = true)
    public List<AdminUserDto> search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            throw new BadRequestException("Вкажіть ID, uid або частину імені користувача");
        }

        Map<Long, User> found = new LinkedHashMap<>();   // без дублікатів, у порядку знаходження
        if (q.matches("\\d{1,18}")) {
            userRepository.findById(Long.parseLong(q)).ifPresent(u -> found.put(u.getUserId(), u));
        }
        userRepository.findByUid(q).ifPresent(u -> found.put(u.getUserId(), u));
        for (User u : userRepository.findTop20ByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(q, q)) {
            found.putIfAbsent(u.getUserId(), u);
        }
        return found.values().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AdminUserDto get(long userId) {
        return toDto(userOf(userId));
    }

    @Transactional
    public AdminUserDto block(long userId, String reason, Long adminId) {
        if (isSelf(userId, adminId)) {
            throw new BadRequestException("Не можна заблокувати власний акаунт");
        }
        UserPrivate p = privateOf(userId);
        if (p.getRole() == UserRole.admin) {
            throw new ConflictException("Не можна заблокувати адміністратора: спершу змініть його роль");
        }
        if (p.isBlocked()) {
            throw new ConflictException("Користувач уже заблокований");
        }

        String cleanReason = reason == null || reason.isBlank() ? null : reason.trim();
        p.setBlocked(true);
        p.setBlockedReason(cleanReason);
        p.setBlockedAt(LocalDateTime.now());

        auditService.log(adminId, AuditAction.USER_BLOCK, TARGET_USER, userId,
                cleanReason == null ? "без причини" : "причина: " + cleanReason);
        afterCommit(() -> chatSessionRegistry.closeAll(userId));
        return toDto(p.getUser());
    }

    @Transactional
    public AdminUserDto unblock(long userId, Long adminId) {
        UserPrivate p = privateOf(userId);
        if (!p.isBlocked()) {
            throw new ConflictException("Користувач не заблокований");
        }
        p.setBlocked(false);
        p.setBlockedReason(null);
        p.setBlockedAt(null);

        auditService.log(adminId, AuditAction.USER_UNBLOCK, TARGET_USER, userId, null);
        return toDto(p.getUser());
    }

    @Transactional
    public AdminUserDto changeRole(long userId, String role, Long adminId) {
        if (isSelf(userId, adminId)) {
            throw new BadRequestException("Не можна змінити власну роль");
        }
        UserRole newRole = UserRole.valueOf(role);   // формат уже перевірено @Pattern у DTO
        UserPrivate p = privateOf(userId);
        UserRole oldRole = p.getRole();
        if (oldRole == newRole) {
            throw new ConflictException("Користувач уже має роль " + newRole.name());
        }
        if (newRole == UserRole.admin && p.isBlocked()) {
            throw new ConflictException("Спершу розблокуйте користувача");
        }
        p.setRole(newRole);

        auditService.log(adminId, AuditAction.USER_ROLE_CHANGE, TARGET_USER, userId,
                oldRole.name() + " → " + newRole.name());
        return toDto(p.getUser());
    }

    @Transactional
    public AdminUserDto updateProfile(long userId, AdminProfileUpdateDto dto, Long adminId) {
        User user = userOf(userId);
        List<String> changed = new ArrayList<>();

        if (dto.displayName() != null) {
            String value = dto.displayName().isBlank() ? null : dto.displayName().trim();
            if (!Objects.equals(value, user.getDisplayName())) {
                user.setDisplayName(value);
                changed.add("displayName");
            }
        }
        if (dto.aboutMe() != null) {
            String value = dto.aboutMe().isBlank() ? null : dto.aboutMe().trim();
            if (!Objects.equals(value, user.getAboutMe())) {
                user.setAboutMe(value);
                changed.add("aboutMe");
            }
        }
        if (dto.removeAvatar() && user.getUserAvatarUrl() != null) {
            user.setUserAvatarUrl(null);
            changed.add("avatar removed");
        }

        if (!changed.isEmpty()) {
            auditService.log(adminId, AuditAction.USER_PROFILE_EDIT, TARGET_USER, userId, String.join(", ", changed));
        }
        return toDto(user);
    }

    /** Новий пароль і відкликання всіх токенів: користувач вийде з усіх пристроїв. */
    @Transactional
    public void resetPassword(long userId, String newPassword, Long adminId) {
        UserPrivate p = privateOf(userId);
        p.setPasswordHash(passwordEncoder.encode(newPassword));
        p.incrementTokenVersion();

        // сам пароль у журнал не пишемо
        auditService.log(adminId, AuditAction.USER_PASSWORD_RESET, TARGET_USER, userId,
                "усі сесії користувача завершено");
        afterCommit(() -> chatSessionRegistry.closeAll(userId));
    }

    private AdminUserDto toDto(User u) {
        UserPrivate p = userPrivateRepository.findByUser(u)
                .orElseThrow(() -> new NotFoundException("Private data not found for user: " + u.getUserId()));
        return new AdminUserDto(
                u.getUserId(), u.getUid(), u.getUsername(), u.getDisplayName(), u.getAboutMe(),
                u.getUserAvatarUrl(), p.getEmail(), p.getRole().name(),
                p.isBlocked(), p.getBlockedReason(), p.getBlockedAt(), u.getCreatedAt(),
                postRepository.countByUser(u),
                followersRepository.countByFollowing(u),
                followersRepository.countByFollower(u));
    }

    private User userOf(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    private UserPrivate privateOf(long userId) {
        return userPrivateRepository.findByUser(userOf(userId))
                .orElseThrow(() -> new NotFoundException("Private data not found for user: " + userId));
    }

    private static boolean isSelf(long userId, Long adminId) {
        return adminId != null && adminId == userId;
    }

    /** Дія, яку треба виконати лише після успішного коміту (закриття WebSocket-сесій). */
    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}