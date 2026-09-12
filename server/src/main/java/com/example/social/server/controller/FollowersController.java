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
        try {
            followersService.follow(currentUser.getUserId(), userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "followed"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<?> unfollow(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                      @PathVariable("userId") Long userId) {
        try {
            followersService.unfollow(currentUser.getUserId(), userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getFollowers(@PathVariable("userId") Long userId) {
        try {
            return ResponseEntity.ok(followersService.getFollowers(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{userId}/following")
    public ResponseEntity<?> getFollowing(@PathVariable("userId") Long userId) {
        try {
            return ResponseEntity.ok(followersService.getFollowing(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{userId}/count")
    public ResponseEntity<?> getFollowersCount(@PathVariable("userId") Long userId) {
        try {
            return ResponseEntity.ok(Map.of("followersCount", followersService.getFollowersCount(userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}