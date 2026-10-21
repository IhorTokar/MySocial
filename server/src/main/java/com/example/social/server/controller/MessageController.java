package com.example.social.server.controller;

import com.example.social.server.exception.BadRequestException;
import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.FileStorageService;
import com.example.social.server.service.MessageService;
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
        return ResponseEntity.ok(messageService.getConversations(currentUser.getUserId()));
    }

    @GetMapping("/with/{userId}")
    public ResponseEntity<?> getConversation(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                             @PathVariable("userId") Long userId) {
        return ResponseEntity.ok(messageService.getConversation(currentUser.getUserId(), userId));
    }

    @PostMapping("/with/{userId}/read")
    public ResponseEntity<?> markRead(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                      @PathVariable("userId") Long userId) {
        int marked = messageService.markConversationRead(currentUser.getUserId(), userId);
        return ResponseEntity.ok(Map.of("marked", marked));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadPhoto(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                         @RequestParam("file") MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BadRequestException("Дозволені лише зображення");
        }
        String url = fileStorageService.store(file);
        return ResponseEntity.ok(Map.of("url", url));
    }
}