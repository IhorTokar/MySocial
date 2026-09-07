package com.example.social.server.repository;

import com.example.social.server.entity.UserPrivate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserPrivateRepository extends JpaRepository<UserPrivate, Long> {
    Optional<UserPrivate> findByEmail(String email);
    boolean existsByEmail(String email);
}