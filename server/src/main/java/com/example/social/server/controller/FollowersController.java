package com.example.social.server.controller;

import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.FollowersService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/followers")
public class FollowersController {

    private final FollowersService followersService;

    public FollowersController(FollowersService followersService) {
        this.followersService = followersService;
    }

    @PostMapping("/{userId}")
    public ResponseEntity<?> follow(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                    @PathVariable("userId") Long userId) {
        followersService.follow(currentUser.getUserId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "followed"));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<?> unfollow(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                      @PathVariable("userId") Long userId) {
        followersService.unfollow(currentUser.getUserId(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getFollowers(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(followersService.getFollowers(userId));
    }

    @GetMapping("/{userId}/following")
    public ResponseEntity<?> getFollowing(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(followersService.getFollowing(userId));
    }

    @GetMapping("/{userId}/count")
    public ResponseEntity<?> getFollowersCount(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(Map.of("followersCount", followersService.getFollowersCount(userId)));
    }

    @GetMapping("/{userId}/following/summary")
    public ResponseEntity<?> getFollowingSummary(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                 @PathVariable("userId") Long userId) {
        Long currentUserId = currentUser != null ? currentUser.getUserId() : null;
        return ResponseEntity.ok(followersService.getFollowingSummary(userId, currentUserId));
    }

    @GetMapping("/{userId}/summary")
    public ResponseEntity<?> getFollowersSummary(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                 @PathVariable("userId") Long userId) {
        Long currentUserId = currentUser != null ? currentUser.getUserId() : null;
        return ResponseEntity.ok(followersService.getFollowersSummary(userId, currentUserId));
    }
}