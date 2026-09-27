package com.example.social.server.controller;

import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.FileStorageService;
import com.example.social.server.service.UserService;
import com.example.social.shared.dto.UpdateProfileDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final FileStorageService fileStorageService;

    public UserController(UserService userService, FileStorageService fileStorageService) {
        this.userService = userService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUser(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                     @PathVariable("userId") Long userId) {
        try {
            Long currentUserId = currentUser != null ? currentUser.getUserId() : null;
            return ResponseEntity.ok(userService.getUserProfile(userId, currentUserId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        try {
            return ResponseEntity.ok(userService.getUserProfile(currentUser.getUserId(), currentUser.getUserId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/me")
    public ResponseEntity<?> updateMyProfile(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                             @Valid @RequestBody UpdateProfileDto dto) {
        try {
            return ResponseEntity.ok(userService.updateProfile(currentUser.getUserId(), dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<?> updateAvatar(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                          @RequestParam("file") MultipartFile file) {
        try {
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Дозволені лише зображення"));
            }

            String avatarUrl = fileStorageService.store(file);
            return ResponseEntity.ok(userService.updateAvatar(currentUser.getUserId(), avatarUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}