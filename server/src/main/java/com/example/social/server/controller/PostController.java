package com.example.social.server.controller;

import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.FileStorageService;
import com.example.social.server.service.PostService;
import com.example.social.shared.dto.PostCreateDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final FileStorageService fileStorageService;

    public PostController(PostService postService, FileStorageService fileStorageService) {
        this.postService = postService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping
    public ResponseEntity<?> createPost(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                        @Valid @RequestBody PostCreateDto dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(postService.createPost(currentUser.getUserId(), dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{postId}/media")
    public ResponseEntity<?> uploadMedia(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                         @PathVariable("postId") Long postId,
                                         @RequestParam("file") MultipartFile file) {
        try {
            if (!postService.isOwner(postId, currentUser.getUserId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You can only edit your own posts"));
            }

            String contentType = file.getContentType();
            if (contentType == null || !(contentType.startsWith("image/") || contentType.startsWith("video/"))) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Only image or video files are allowed"));
            }

            String mediaUrl = fileStorageService.store(file);
            return ResponseEntity.ok(postService.updatePostMedia(postId, mediaUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{postId}")
    public ResponseEntity<?> getPost(@PathVariable("postId") Long postId) {
        try {
            return ResponseEntity.ok(postService.getPost(postId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getPostsByUser(@PathVariable("userId") Long userId) {
        try {
            return ResponseEntity.ok(postService.getPostsByUser(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/feed")
    public ResponseEntity<?> getFeed(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        try {
            return ResponseEntity.ok(postService.getFeed(currentUser.getUserId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<?> deletePost(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                        @PathVariable("postId") Long postId) {
        try {
            if (!postService.isOwner(postId, currentUser.getUserId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You can only delete your own posts"));
            }
            postService.deletePost(postId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}