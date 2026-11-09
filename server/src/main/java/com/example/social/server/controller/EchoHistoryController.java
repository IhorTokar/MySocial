package com.example.social.server.controller;

import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.EchoHistoryService;
import com.example.social.shared.dto.EchoSnapshotDetailDto;
import com.example.social.shared.dto.EchoSnapshotDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Історія ехо-камер. Лише для адміністратора (шлях під /api/admin/**). */
@RestController
@RequestMapping("/api/admin/echo-history")
public class EchoHistoryController {

    private static final int MAX_LIMIT = 500;

    private final EchoHistoryService echoHistoryService;

    public EchoHistoryController(EchoHistoryService echoHistoryService) {
        this.echoHistoryService = echoHistoryService;
    }

    /** Зробити знімок зараз. refresh=true — спершу оновити граф і заново визначити спільноти. */
    @PostMapping
    public ResponseEntity<EchoSnapshotDto> takeSnapshot(@AuthenticationPrincipal AuthenticatedUser admin,
                                                        @RequestParam(name = "leiden", defaultValue = "false") boolean leiden,
                                                        @RequestParam(name = "refresh", defaultValue = "true") boolean refresh) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(echoHistoryService.takeSnapshot(leiden, refresh, admin.getUserId()));
    }

    /** Часовий ряд знімків — від найстарішого. */
    @GetMapping
    public ResponseEntity<List<EchoSnapshotDto>> history(@RequestParam(name = "leiden", defaultValue = "false") boolean leiden,
                                                         @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return ResponseEntity.ok(echoHistoryService.history(leiden, clamp(limit)));
    }

    @GetMapping("/{snapshotId}")
    public ResponseEntity<EchoSnapshotDetailDto> snapshot(@PathVariable("snapshotId") long snapshotId) {
        return ResponseEntity.ok(echoHistoryService.snapshotDetail(snapshotId));
    }

    static int clamp(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}