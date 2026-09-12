package com.example.social.server.repository;

import com.example.social.server.entity.User;
import com.example.social.server.entity.UserPrivate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserPrivateRepository extends JpaRepository<UserPrivate, Long> {
    Optional<UserPrivate> findByEmail(String email);
    Optional<UserPrivate> findByUser(User user);
    boolean existsByEmail(String email);
}