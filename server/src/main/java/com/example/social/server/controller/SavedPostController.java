package com.example.social.server.controller;

import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.SavedPostService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class SavedPostController {

    private final SavedPostService savedPostService;

    public SavedPostController(SavedPostService savedPostService) {
        this.savedPostService = savedPostService;
    }

    @PostMapping("/api/posts/{postId}/save")
    public ResponseEntity<?> save(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                  @PathVariable("postId") Long postId) {
        try {
            savedPostService.save(postId, currentUser.getUserId());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "saved"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/posts/{postId}/save/status")
    public ResponseEntity<?> getStatus(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                       @PathVariable("postId") Long postId) {
        try {
            boolean saved = savedPostService.isSavedByUser(postId, currentUser.getUserId());
            return ResponseEntity.ok(Map.of("saved", saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/posts/{postId}/save")
    public ResponseEntity<?> unsave(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                    @PathVariable("postId") Long postId) {
        try {
            savedPostService.unsave(postId, currentUser.getUserId());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/posts/saved")
    public ResponseEntity<?> getSaved(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(savedPostService.getSavedPosts(currentUser.getUserId()));
    }
}