package com.example.social.server.service;

import com.example.social.server.entity.Followers;
import com.example.social.server.entity.User;
import com.example.social.server.repository.FollowersRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.shared.dto.FollowerResponseDto;
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

    private FollowerResponseDto toDto(User user, java.time.LocalDateTime followedSince) {
        return new FollowerResponseDto(
                user.getUserId(),
                user.getUsername(),
                user.getDisplayName(),
                followedSince
        );
    }
}