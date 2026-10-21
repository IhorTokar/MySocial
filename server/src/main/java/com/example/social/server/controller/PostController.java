package com.example.social.server.controller;

import com.example.social.server.exception.BadRequestException;
import com.example.social.server.exception.ForbiddenException;
import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.FileStorageService;
import com.example.social.server.service.PostService;
import com.example.social.server.service.RecommendationService;
import com.example.social.shared.dto.PostCreateDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final RecommendationService recommendationService;
    private final FileStorageService fileStorageService;

    public PostController(PostService postService,
                          RecommendationService recommendationService,
                          FileStorageService fileStorageService) {
        this.postService = postService;
        this.recommendationService = recommendationService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping
    public ResponseEntity<?> createPost(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                        @Valid @RequestBody PostCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(postService.createPost(currentUser.getUserId(), dto));
    }

    @PostMapping("/{postId}/media")
    public ResponseEntity<?> uploadMedia(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                         @PathVariable("postId") Long postId,
                                         @RequestParam("file") MultipartFile file) {
        if (!postService.isOwner(postId, currentUser.getUserId())) {
            throw new ForbiddenException("You can only edit your own posts");
        }

        String contentType = file.getContentType();
        if (contentType == null || !(contentType.startsWith("image/") || contentType.startsWith("video/"))) {
            throw new BadRequestException("Only image or video files are allowed");
        }

        String mediaUrl = fileStorageService.store(file);
        return ResponseEntity.ok(postService.updatePostMedia(postId, mediaUrl));
    }

    @GetMapping("/feed/debug")
    public ResponseEntity<?> getFeedDebug(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                          @RequestParam(name = "limit", defaultValue = "30") int limit) {
        return ResponseEntity.ok(recommendationService.getHybridFeedDebug(currentUser.getUserId(), limit));
    }

    @GetMapping("/feed")
    public ResponseEntity<?> getFeed(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                     @RequestParam(name = "limit", defaultValue = "30") int limit) {
        return ResponseEntity.ok(recommendationService.getHybridFeed(currentUser.getUserId(), limit));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<?> deletePost(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                        @PathVariable("postId") Long postId) {
        if (!postService.isOwner(postId, currentUser.getUserId())) {
            throw new ForbiddenException("You can only delete your own posts");
        }
        postService.deletePost(postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{postId}")
    public ResponseEntity<?> getPost(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                     @PathVariable("postId") Long postId) {
        return ResponseEntity.ok(postService.getPost(postId, currentUser.getUserId()));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getPostsByUser(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                            @PathVariable("userId") Long userId) {
        return ResponseEntity.ok(postService.getPostsByUser(userId, currentUser.getUserId()));
    }

    @PatchMapping("/{postId}")
    public ResponseEntity<?> updatePost(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                        @PathVariable("postId") Long postId,
                                        @Valid @RequestBody PostCreateDto dto) {
        return ResponseEntity.ok(postService.updatePost(postId, currentUser.getUserId(), dto));
    }
}