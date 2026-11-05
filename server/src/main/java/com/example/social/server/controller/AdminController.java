package com.example.social.server.controller;

import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.AuditService;
import com.example.social.server.service.RecommendationWeightsService;
import com.example.social.shared.dto.AuditLogEntryDto;
import com.example.social.shared.dto.RecommendationWeightsDto;
import com.example.social.shared.dto.WeightsUpdateRequestDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Адмін-панель. Доступ лише для ролі admin (див. SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final int MAX_LIMIT = 500;

    private final RecommendationWeightsService weightsService;
    private final AuditService auditService;

    public AdminController(RecommendationWeightsService weightsService, AuditService auditService) {
        this.weightsService = weightsService;
        this.auditService = auditService;
    }

    @GetMapping("/weights")
    public ResponseEntity<RecommendationWeightsDto> getWeights() {
        return ResponseEntity.ok(weightsService.currentDto());
    }

    @PutMapping("/weights")
    public ResponseEntity<RecommendationWeightsDto> updateWeights(@AuthenticationPrincipal AuthenticatedUser admin,
                                                                  @Valid @RequestBody WeightsUpdateRequestDto request) {
        return ResponseEntity.ok(weightsService.update(request, admin.getUserId()));
    }

    @GetMapping("/weights/history")
    public ResponseEntity<List<RecommendationWeightsDto>> getWeightsHistory(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(weightsService.history(clamp(limit)));
    }

    @PostMapping("/weights/history/{versionId}/restore")
    public ResponseEntity<RecommendationWeightsDto> restoreWeights(@AuthenticationPrincipal AuthenticatedUser admin,
                                                                   @PathVariable("versionId") long versionId) {
        return ResponseEntity.ok(weightsService.restore(versionId, admin.getUserId()));
    }

    @GetMapping("/audit")
    public ResponseEntity<List<AuditLogEntryDto>> getAuditLog(
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return ResponseEntity.ok(auditService.recent(clamp(limit)));
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}