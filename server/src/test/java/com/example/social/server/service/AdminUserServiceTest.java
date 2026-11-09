package com.example.social.server.service;

import com.example.social.server.entity.AuditAction;
import com.example.social.server.entity.User;
import com.example.social.server.entity.UserPrivate;
import com.example.social.server.entity.UserRole;
import com.example.social.server.exception.BadRequestException;
import com.example.social.server.exception.ConflictException;
import com.example.social.server.repository.FollowersRepository;
import com.example.social.server.repository.PostRepository;
import com.example.social.server.repository.UserPrivateRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.server.websocket.ChatSessionRegistry;
import com.example.social.shared.dto.AdminUserDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Дії адміністратора над користувачами: запобіжники, стан після дії, журнал, закриття чату після коміту. */
class AdminUserServiceTest {

    private static final long ADMIN_ID = 83L;
    private static final long USER_ID = 89L;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserPrivateRepository userPrivateRepository = mock(UserPrivateRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AuditService auditService = mock(AuditService.class);
    private final ChatSessionRegistry chatSessionRegistry = mock(ChatSessionRegistry.class);

    private final AdminUserService service = new AdminUserService(userRepository, userPrivateRepository,
            mock(PostRepository.class), mock(FollowersRepository.class), passwordEncoder, auditService,
            chatSessionRegistry);

    private UserPrivate admin;
    private UserPrivate user;

    @BeforeEach
    void setUp() {
        admin = account(ADMIN_ID, "medoko", UserRole.admin);
        user = account(USER_ID, "ВеселийШторм356", UserRole.user);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    /** Користувач і його приватні дані, які повертатимуть замокані репозиторії. */
    private UserPrivate account(long id, String username, UserRole role) {
        User u = new User();
        u.setUserId(id);
        u.setUsername(username);
        UserPrivate p = new UserPrivate();
        p.setUser(u);
        p.setRole(role);
        p.setEmail(username + "@example.com");
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(userPrivateRepository.findByUser(u)).thenReturn(Optional.of(p));
        return p;
    }

    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    // ===== Блокування =====

    @Test
    void blockStoresReasonAuditsAndClosesChatOnlyAfterCommit() {
        AdminUserDto dto = service.block(USER_ID, "  спам  ", ADMIN_ID);

        assertTrue(user.isBlocked());
        assertEquals("спам", user.getBlockedReason());
        assertNotNull(user.getBlockedAt());
        assertTrue(dto.blocked());
        verify(auditService).log(ADMIN_ID, AuditAction.USER_BLOCK, "USER", USER_ID, "причина: спам");

        verify(chatSessionRegistry, never()).closeAll(any());
        commit();
        verify(chatSessionRegistry).closeAll(USER_ID);
    }

    @Test
    void blankReasonIsStoredAsNull() {
        service.block(USER_ID, "   ", ADMIN_ID);

        assertNull(user.getBlockedReason());
        verify(auditService).log(ADMIN_ID, AuditAction.USER_BLOCK, "USER", USER_ID, "без причини");
    }

    @Test
    void cannotBlockYourself() {
        assertThrows(BadRequestException.class, () -> service.block(ADMIN_ID, null, ADMIN_ID));
        assertFalse(admin.isBlocked());
        verify(auditService, never()).log(any(), any(), any(), any(), any());
    }

    @Test
    void cannotBlockAnotherAdmin() {
        account(90L, "other_admin", UserRole.admin);

        assertThrows(ConflictException.class, () -> service.block(90L, null, ADMIN_ID));
    }

    @Test
    void unblockClearsAllBlockFields() {
        service.block(USER_ID, "спам", ADMIN_ID);
        service.unblock(USER_ID, ADMIN_ID);

        assertFalse(user.isBlocked());
        assertNull(user.getBlockedReason());
        assertNull(user.getBlockedAt());
        assertThrows(ConflictException.class, () -> service.unblock(USER_ID, ADMIN_ID));
    }

    // ===== Роль =====

    @Test
    void changeRoleRecordsOldAndNewRole() {
        service.changeRole(USER_ID, "guest", ADMIN_ID);

        assertEquals(UserRole.guest, user.getRole());
        verify(auditService).log(ADMIN_ID, AuditAction.USER_ROLE_CHANGE, "USER", USER_ID, "user → guest");
    }

    @Test
    void cannotChangeOwnRole() {
        assertThrows(BadRequestException.class, () -> service.changeRole(ADMIN_ID, "user", ADMIN_ID));
        assertEquals(UserRole.admin, admin.getRole());
    }

    @Test
    void blockedUserCannotBecomeAdmin() {
        service.block(USER_ID, null, ADMIN_ID);

        assertThrows(ConflictException.class, () -> service.changeRole(USER_ID, "admin", ADMIN_ID));
    }

    // ===== Пароль =====

    @Test
    void resetPasswordRevokesTokensAndNeverLogsThePassword() {
        when(passwordEncoder.encode("newpass123")).thenReturn("HASH");

        service.resetPassword(USER_ID, "newpass123", ADMIN_ID);

        assertEquals("HASH", user.getPasswordHash());
        assertEquals(1, user.getTokenVersion(), "старі JWT мають стати недійсними");

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditService).log(eq(ADMIN_ID), eq(AuditAction.USER_PASSWORD_RESET), eq("USER"), eq(USER_ID),
                details.capture());
        assertFalse(details.getValue().contains("newpass123"));

        commit();
        verify(chatSessionRegistry).closeAll(USER_ID);
    }

    // ===== Пошук =====

    @Test
    void searchByIdDoesNotDuplicateUserFoundAlsoByName() {
        User u = user.getUser();
        when(userRepository.findTop20ByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase("89", "89"))
                .thenReturn(List.of(u));

        List<AdminUserDto> result = service.search("89");

        assertEquals(1, result.size());
        assertEquals(USER_ID, result.get(0).userId());
    }

    @Test
    void emptySearchIsRejected() {
        assertThrows(BadRequestException.class, () -> service.search("  "));
    }
}