package com.example.social.server.controller;

import com.example.social.server.exception.BadRequestException;
import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.FileStorageService;
import com.example.social.server.service.UserService;
import com.example.social.shared.dto.ChangePasswordDto;
import com.example.social.shared.dto.DeleteAccountDto;
import com.example.social.shared.dto.UpdateProfileDto;
import jakarta.validation.Valid;
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
        Long currentUserId = currentUser != null ? currentUser.getUserId() : null;
        return ResponseEntity.ok(userService.getUserProfile(userId, currentUserId));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(userService.getUserProfile(currentUser.getUserId(), currentUser.getUserId()));
    }

    @PatchMapping("/me")
    public ResponseEntity<?> updateMyProfile(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                             @Valid @RequestBody UpdateProfileDto dto) {
        return ResponseEntity.ok(userService.updateProfile(currentUser.getUserId(), dto));
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<?> updateAvatar(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                          @RequestParam("file") MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("Дозволені лише зображення");
        }

        String avatarUrl = fileStorageService.store(file);
        return ResponseEntity.ok(userService.updateAvatar(currentUser.getUserId(), avatarUrl));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                            @Valid @RequestBody ChangePasswordDto dto) {
        userService.changePassword(currentUser.getUserId(), dto);
        return ResponseEntity.ok(Map.of("status", "password changed"));
    }

    @DeleteMapping("/me")
    public ResponseEntity<?> deleteAccount(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                           @Valid @RequestBody DeleteAccountDto dto) {
        userService.deleteAccount(currentUser.getUserId(), dto.getPassword());
        return ResponseEntity.noContent().build();
    }
}