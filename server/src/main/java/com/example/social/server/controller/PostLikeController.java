package com.example.social.server.controller;

import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.PostLikeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/posts/{postId}/like")
public class PostLikeController {

    private final PostLikeService postLikeService;

    public PostLikeController(PostLikeService postLikeService) {
        this.postLikeService = postLikeService;
    }

    @PostMapping
    public ResponseEntity<?> like(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                  @PathVariable("postId") Long postId) {
        try {
            postLikeService.like(postId, currentUser.getUserId());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "liked"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                       @PathVariable("postId") Long postId) {
        try {
            boolean liked = postLikeService.isLikedByUser(postId, currentUser.getUserId());
            long count = postLikeService.getLikesCount(postId);
            return ResponseEntity.ok(Map.of("liked", liked, "likesCount", count));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<?> unlike(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                    @PathVariable("postId") Long postId) {
        try {
            postLikeService.unlike(postId, currentUser.getUserId());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/count")
    public ResponseEntity<?> getCount(@PathVariable("postId") Long postId) {
        try {
            return ResponseEntity.ok(Map.of("likesCount", postLikeService.getLikesCount(postId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}