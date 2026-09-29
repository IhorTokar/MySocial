package com.example.social.server.controller;

import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.FileStorageService;
import com.example.social.server.service.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final FileStorageService fileStorageService;

    public MessageController(MessageService messageService, FileStorageService fileStorageService) {
        this.messageService = messageService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        try {
            return ResponseEntity.ok(messageService.getConversations(currentUser.getUserId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/with/{userId}")
    public ResponseEntity<?> getConversation(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                             @PathVariable("userId") Long userId) {
        try {
            return ResponseEntity.ok(messageService.getConversation(currentUser.getUserId(), userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/with/{userId}/read")
    public ResponseEntity<?> markRead(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                      @PathVariable("userId") Long userId) {
        try {
            int marked = messageService.markConversationRead(currentUser.getUserId(), userId);
            return ResponseEntity.ok(Map.of("marked", marked));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadPhoto(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                         @RequestParam("file") MultipartFile file) {
        try {
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Дозволені лише зображення"));
            }
            String url = fileStorageService.store(file);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Не вдалося завантажити фото: " + e.getMessage()));
        }
    }
}