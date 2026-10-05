package com.example.social.server.service;

import com.example.social.server.entity.*;
import com.example.social.server.repository.*;
import com.example.social.shared.dto.ChangePasswordDto;
import com.example.social.shared.dto.UpdateProfileDto;
import com.example.social.shared.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserPrivateRepository userPrivateRepository;
    private final PasswordEncoder passwordEncoder;
    private final PostRepository postRepository;
    private final FollowersRepository followersRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;
    private final SavedPostRepository savedPostRepository;
    private final MessageRepository messageRepository;

    public UserService(UserRepository userRepository,
                       UserPrivateRepository userPrivateRepository,
                       PasswordEncoder passwordEncoder,
                       PostRepository postRepository,
                       FollowersRepository followersRepository,
                       PostLikeRepository postLikeRepository,
                       CommentRepository commentRepository,
                       SavedPostRepository savedPostRepository,
                       MessageRepository messageRepository) {
        this.userRepository = userRepository;
        this.userPrivateRepository = userPrivateRepository;
        this.passwordEncoder = passwordEncoder;
        this.postRepository = postRepository;
        this.followersRepository = followersRepository;
        this.postLikeRepository = postLikeRepository;
        this.commentRepository = commentRepository;
        this.savedPostRepository = savedPostRepository;
        this.messageRepository = messageRepository;
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

    @Transactional
    public void changePassword(Long userId, ChangePasswordDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        UserPrivate userPrivate = userPrivateRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Private data not found for user: " + userId));

        if (!passwordEncoder.matches(dto.getCurrentPassword(), userPrivate.getPasswordHash())) {
            throw new SecurityException("Current password is incorrect");
        }

        userPrivate.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        userPrivateRepository.save(userPrivate);
        log.info("Password changed for user {}", userId);
    }

    /**
     * Повне видалення акаунту: спершу прибираються всі залежні записи
     * (лайки, коментарі, збережені пости, підписки, повідомлення, пости),
     * потім сам User/UserPrivate. Порядок важливий через FK-обмеження.
     */
    @Transactional
    public void deleteAccount(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        UserPrivate userPrivate = userPrivateRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Private data not found for user: " + userId));

        if (!passwordEncoder.matches(password, userPrivate.getPasswordHash())) {
            throw new SecurityException("Password is incorrect");
        }

        // 1. Повідомлення (як відправник, так і отримувач)
        List<Message> messages = messageRepository.findAllByUser(user);
        messageRepository.deleteAll(messages);

        // 2. Лайки постів
        postLikeRepository.deleteAll(postLikeRepository.findByUser(user));

        // 3. Коментарі
        commentRepository.deleteAll(commentRepository.findByUser(user));

        // 4. Збережені пости
        savedPostRepository.deleteAll(savedPostRepository.findByUser(user));

        // 5. Підписки (в обидва боки)
        followersRepository.deleteAll(followersRepository.findByFollower(user));
        followersRepository.deleteAll(followersRepository.findByFollowing(user));

        // 6. Пости користувача — спершу чужі лайки/коментарі на них, потім самі пости
        List<Post> myPosts = postRepository.findByUser(user);
        if (!myPosts.isEmpty()) {
            postLikeRepository.deleteAll(postLikeRepository.findByPostIn(myPosts));
            commentRepository.deleteAll(commentRepository.findByPostIn(myPosts));
            savedPostRepository.deleteAll(myPosts.stream()
                    .flatMap(p -> savedPostRepository.findByPost(p).stream())
                    .collect(java.util.stream.Collectors.toList()));
            postRepository.deleteAll(myPosts);
        }

        // 7. Приватні дані й сам акаунт
        userPrivateRepository.delete(userPrivate);
        userRepository.delete(user);

        log.info("Account deleted: userId={}", userId);
    }
}