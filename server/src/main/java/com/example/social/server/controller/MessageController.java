package com.example.social.server.controller;

import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
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
}