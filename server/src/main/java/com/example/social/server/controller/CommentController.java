package com.example.social.server.controller;

import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.CommentService;
import com.example.social.shared.dto.CommentCreateDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<?> addComment(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                        @PathVariable("postId") Long postId,
                                        @Valid @RequestBody CommentCreateDto dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(commentService.addComment(postId, currentUser.getUserId(), dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<?> getComments(@PathVariable("postId") Long postId) {
        try {
            return ResponseEntity.ok(commentService.getComments(postId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                           @PathVariable("commentId") Long commentId) {
        try {
            commentService.deleteComment(commentId, currentUser.getUserId());
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}