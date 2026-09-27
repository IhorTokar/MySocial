package com.example.social.server.service;

import com.example.social.server.entity.User;
import com.example.social.server.entity.UserPrivate;
import com.example.social.server.entity.UserRole;
import com.example.social.server.repository.FollowersRepository;
import com.example.social.server.repository.PostRepository;
import com.example.social.server.repository.UserPrivateRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.shared.dto.UpdateProfileDto;
import com.example.social.shared.dto.UserDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserPrivateRepository userPrivateRepository;
    private final PasswordEncoder passwordEncoder;
    private final PostRepository postRepository;
    private final FollowersRepository followersRepository;

    public UserService(UserRepository userRepository,
                       UserPrivateRepository userPrivateRepository,
                       PasswordEncoder passwordEncoder,
                       PostRepository postRepository,
                       FollowersRepository followersRepository) {
        this.userRepository = userRepository;
        this.userPrivateRepository = userPrivateRepository;
        this.passwordEncoder = passwordEncoder;
        this.postRepository = postRepository;
        this.followersRepository = followersRepository;
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

    @Transactional(readOnly = true)
    public UserDto getUserProfile(Long targetUserId, Long currentUserId) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + targetUserId));

        long postsCount = postRepository.countByUser(target);
        long followersCount = followersRepository.countByFollowing(target);
        long followingCount = followersRepository.countByFollower(target);

        boolean followedByCurrentUser = false;
        if (currentUserId != null && !currentUserId.equals(targetUserId)) {
            User current = userRepository.findById(currentUserId).orElse(null);
            if (current != null) {
                followedByCurrentUser = followersRepository.existsByFollowerAndFollowing(current, target);
            }
        }

        return new UserDto(
                target.getUserId(),
                target.getUid(),
                target.getUsername(),
                target.getDisplayName(),
                target.getAboutMe(),
                target.getUserAvatarUrl(),
                postsCount,
                followersCount,
                followingCount,
                followedByCurrentUser
        );
    }

    @Transactional
    public UserDto updateProfile(Long userId, UpdateProfileDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (dto.getDisplayName() != null) {
            user.setDisplayName(dto.getDisplayName().isBlank() ? null : dto.getDisplayName().trim());
        }
        if (dto.getAboutMe() != null) {
            user.setAboutMe(dto.getAboutMe().isBlank() ? null : dto.getAboutMe().trim());
        }

        userRepository.save(user);
        return getUserProfile(userId, userId);
    }

    @Transactional
    public UserDto updateAvatar(Long userId, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.setUserAvatarUrl(avatarUrl);
        userRepository.save(user);
        return getUserProfile(userId, userId);
    }
}