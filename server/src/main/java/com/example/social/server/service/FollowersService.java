package com.example.social.server.service;

import com.example.social.server.entity.Followers;
import com.example.social.server.entity.User;
import com.example.social.server.repository.FollowersRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.shared.dto.FollowerResponseDto;
import com.example.social.shared.dto.UserSummaryDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FollowersService {

    private final FollowersRepository followersRepository;
    private final UserRepository userRepository;

    public FollowersService(FollowersRepository followersRepository, UserRepository userRepository) {
        this.followersRepository = followersRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new IllegalArgumentException("You cannot follow yourself");
        }

        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + followerId));
        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + followingId));

        if (followersRepository.findByFollowerAndFollowing(follower, following).isPresent()) {
            throw new IllegalArgumentException("Already following this user");
        }

        Followers relation = new Followers();
        relation.setFollower(follower);
        relation.setFollowing(following);
        followersRepository.save(relation);
    }

    @Transactional
    public void unfollow(Long followerId, Long followingId) {
        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + followerId));
        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + followingId));

        Followers relation = followersRepository.findByFollowerAndFollowing(follower, following)
                .orElseThrow(() -> new IllegalArgumentException("You are not following this user"));

        followersRepository.delete(relation);
    }

    @Transactional(readOnly = true)
    public List<FollowerResponseDto> getFollowers(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return followersRepository.findByFollowing(user).stream()
                .map(rel -> toDto(rel.getFollower(), rel.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FollowerResponseDto> getFollowing(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return followersRepository.findByFollower(user).stream()
                .map(rel -> toDto(rel.getFollowing(), rel.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getFollowersCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        return followersRepository.countByFollowing(user);
    }

    /** Список людей, на яких підписаний userId — з відміткою, чи currentUserId сам на них підписаний. */
    @Transactional(readOnly = true)
    public List<UserSummaryDto> getFollowingSummary(Long userId, Long currentUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        List<User> following = followersRepository.findByFollower(user).stream()
                .map(Followers::getFollowing)
                .collect(Collectors.toList());

        return toSummaries(following, currentUserId);
    }

    /** Список тих, хто підписаний на userId — з відміткою, чи currentUserId сам на них підписаний. */
    @Transactional(readOnly = true)
    public List<UserSummaryDto> getFollowersSummary(Long userId, Long currentUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        List<User> followers = followersRepository.findByFollowing(user).stream()
                .map(Followers::getFollower)
                .collect(Collectors.toList());

        return toSummaries(followers, currentUserId);
    }

    private List<UserSummaryDto> toSummaries(List<User> users, Long currentUserId) {
        User currentUser = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;

        return users.stream()
                .map(u -> new UserSummaryDto(
                        u.getUserId(),
                        u.getUsername(),
                        u.getDisplayName(),
                        u.getUserAvatarUrl(),
                        currentUser != null && !currentUser.getUserId().equals(u.getUserId())
                                && followersRepository.existsByFollowerAndFollowing(currentUser, u)
                ))
                .collect(Collectors.toList());
    }

    private FollowerResponseDto toDto(User user, java.time.LocalDateTime followedSince) {
        return new FollowerResponseDto(
                user.getUserId(),
                user.getUsername(),
                user.getDisplayName(),
                followedSince
        );
    }
}