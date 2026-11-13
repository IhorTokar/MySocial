package com.example.social.server.controller;

import com.example.social.server.exception.BadRequestException;
import com.example.social.server.repository.NotificationRepository;
import com.example.social.server.security.AuthenticatedUser;
import com.example.social.shared.dto.NotificationDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Сповіщення: нові читачі, вподобання, коментарі, відповіді. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final int MAX_LIMIT = 100;

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> latest(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                        @RequestParam(name = "limit", defaultValue = "30") int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BadRequestException("limit має бути від 1 до " + MAX_LIMIT);
        }
        return ResponseEntity.ok(notificationRepository.latest(currentUser.getUserId(), limit));
    }

    /** Для значка в шапці: клієнт питає раз на хвилину. */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(Map.of("unread", notificationRepository.unreadCount(currentUser.getUserId())));
    }

    /** Список відкрито — усе, що є зараз, стає прочитаним. */
    @PostMapping("/seen")
    public ResponseEntity<Void> markSeen(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        notificationRepository.markSeen(currentUser.getUserId());
        return ResponseEntity.noContent().build();
    }
}