package com.example.social.server.repository;

import com.example.social.server.entity.User;
import com.example.social.server.entity.UserPrivate;
import com.example.social.server.entity.UserRole;
import com.example.social.server.security.AuthState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserPrivateRepository extends JpaRepository<UserPrivate, Long> {
    Optional<UserPrivate> findByEmail(String email);
    Optional<UserPrivate> findByUser(User user);
    boolean existsByEmail(String email);
    Optional<UserPrivate> findByPhoneNum(String phoneNum);

    /** Роль користувача за його ID (без завантаження всього профілю). */
    @Query("select p.role from UserPrivate p where p.user.userId = :userId")
    Optional<UserRole> findRoleByUserId(@Param("userId") Long userId);
    /** Роль, блокування й версія токенів одним запитом — для перевірки кожного запиту з JWT. */
    @Query("select new com.example.social.server.security.AuthState(p.role, p.blocked, p.tokenVersion) "
            + "from UserPrivate p where p.user.userId = :userId")
    Optional<AuthState> findAuthStateByUserId(@Param("userId") Long userId);
}
