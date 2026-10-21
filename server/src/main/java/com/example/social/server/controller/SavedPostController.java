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
        savedPostService.save(postId, currentUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "saved"));
    }

    @GetMapping("/api/posts/{postId}/save/status")
    public ResponseEntity<?> getStatus(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                       @PathVariable("postId") Long postId) {
        boolean saved = savedPostService.isSavedByUser(postId, currentUser.getUserId());
        return ResponseEntity.ok(Map.of("saved", saved));
    }

    @DeleteMapping("/api/posts/{postId}/save")
    public ResponseEntity<?> unsave(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                    @PathVariable("postId") Long postId) {
        savedPostService.unsave(postId, currentUser.getUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/posts/saved")
    public ResponseEntity<?> getSaved(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(savedPostService.getSavedPosts(currentUser.getUserId()));
    }
}