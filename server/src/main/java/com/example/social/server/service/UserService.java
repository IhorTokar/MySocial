package com.example.social.server.service;

import com.example.social.server.entity.User;
import com.example.social.server.entity.UserPrivate;
import com.example.social.server.entity.UserRole;
import com.example.social.server.repository.UserPrivateRepository;
import com.example.social.server.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserPrivateRepository userPrivateRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       UserPrivateRepository userPrivateRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userPrivateRepository = userPrivateRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User registerUser(String username, String email, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken: " + username);
        }
        if (userPrivateRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }

        User user = new User();
        user.setUid(generateUid());
        user.setUsername(username);
        userRepository.save(user);

        UserPrivate userPrivate = new UserPrivate();
        userPrivate.setUser(user);
        userPrivate.setEmail(email);
        userPrivate.setPasswordHash(passwordEncoder.encode(rawPassword));
        userPrivate.setRole(UserRole.user);
        userPrivateRepository.save(userPrivate);

        return user;
    }

    private String generateUid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}