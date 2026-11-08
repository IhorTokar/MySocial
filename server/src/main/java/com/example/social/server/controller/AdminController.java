package com.example.social.server.controller;

import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.AuditService;
import com.example.social.server.service.RecommendationWeightsService;
import com.example.social.shared.dto.AuditLogEntryDto;
import com.example.social.shared.dto.RecommendationWeightsDto;
import com.example.social.shared.dto.WeightsUpdateRequestDto;
import com.example.social.server.service.AdminUserService;
import com.example.social.shared.dto.AdminPasswordResetDto;
import com.example.social.shared.dto.AdminProfileUpdateDto;
import com.example.social.shared.dto.AdminUserDto;
import com.example.social.shared.dto.BlockUserRequestDto;
import com.example.social.shared.dto.ChangeRoleRequestDto;
import com.example.social.server.service.AdminContentService;
import com.example.social.shared.dto.AdminCommentDto;
import com.example.social.shared.dto.PostResponseDto;
import com.example.social.server.service.CommunityDetectionService;
import com.example.social.shared.dto.GraphDataDto;
import java.util.Map;
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
    private final AdminUserService adminUserService;
    private final AdminContentService adminContentService;
    private final CommunityDetectionService communityDetectionService;

    public AdminController(RecommendationWeightsService weightsService,
                           AuditService auditService,
                           AdminUserService adminUserService,
                           AdminContentService adminContentService,
                           CommunityDetectionService communityDetectionService) {
        this.weightsService = weightsService;
        this.auditService = auditService;
        this.adminUserService = adminUserService;
        this.adminContentService = adminContentService;
        this.communityDetectionService = communityDetectionService;
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

    // ===== Користувачі =====

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserDto>> searchUsers(@RequestParam("q") String query) {
        return ResponseEntity.ok(adminUserService.search(query));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<AdminUserDto> getUser(@PathVariable("userId") long userId) {
        return ResponseEntity.ok(adminUserService.get(userId));
    }

    @PostMapping("/users/{userId}/block")
    public ResponseEntity<AdminUserDto> blockUser(@AuthenticationPrincipal AuthenticatedUser admin,
                                                  @PathVariable("userId") long userId,
                                                  @Valid @RequestBody(required = false) BlockUserRequestDto request) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(adminUserService.block(userId, reason, admin.getUserId()));
    }

    @PostMapping("/users/{userId}/unblock")
    public ResponseEntity<AdminUserDto> unblockUser(@AuthenticationPrincipal AuthenticatedUser admin,
                                                    @PathVariable("userId") long userId) {
        return ResponseEntity.ok(adminUserService.unblock(userId, admin.getUserId()));
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<AdminUserDto> changeRole(@AuthenticationPrincipal AuthenticatedUser admin,
                                                   @PathVariable("userId") long userId,
                                                   @Valid @RequestBody ChangeRoleRequestDto request) {
        return ResponseEntity.ok(adminUserService.changeRole(userId, request.role(), admin.getUserId()));
    }

    @PatchMapping("/users/{userId}/profile")
    public ResponseEntity<AdminUserDto> updateProfile(@AuthenticationPrincipal AuthenticatedUser admin,
                                                      @PathVariable("userId") long userId,
                                                      @Valid @RequestBody AdminProfileUpdateDto request) {
        return ResponseEntity.ok(adminUserService.updateProfile(userId, request, admin.getUserId()));
    }

    @PostMapping("/users/{userId}/password")
    public ResponseEntity<Map<String, String>> resetPassword(@AuthenticationPrincipal AuthenticatedUser admin,
                                                             @PathVariable("userId") long userId,
                                                             @Valid @RequestBody AdminPasswordResetDto request) {
        adminUserService.resetPassword(userId, request.newPassword(), admin.getUserId());
        return ResponseEntity.ok(Map.of("status", "password reset"));
    }

    // ===== Пости й коментарі =====

    @GetMapping("/posts/{postId}")
    public ResponseEntity<PostResponseDto> getPost(@AuthenticationPrincipal AuthenticatedUser admin,
                                                   @PathVariable("postId") long postId) {
        return ResponseEntity.ok(adminContentService.getPost(postId, admin.getUserId()));
    }

    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Void> deletePost(@AuthenticationPrincipal AuthenticatedUser admin,
                                           @PathVariable("postId") long postId,
                                           @RequestParam(name = "reason", required = false) String reason) {
        adminContentService.deletePost(postId, reason, admin.getUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/comments/{commentId}")
    public ResponseEntity<AdminCommentDto> getComment(@PathVariable("commentId") long commentId) {
        return ResponseEntity.ok(adminContentService.getComment(commentId));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@AuthenticationPrincipal AuthenticatedUser admin,
                                              @PathVariable("commentId") long commentId,
                                              @RequestParam(name = "reason", required = false) String reason) {
        adminContentService.deleteComment(commentId, reason, admin.getUserId());
        return ResponseEntity.noContent().build();
    }

    // ===== Граф =====

    @GetMapping("/graph")
    public ResponseEntity<GraphDataDto> getGraph(@RequestParam(name = "leiden", defaultValue = "false") boolean leiden) {
        return ResponseEntity.ok(communityDetectionService.getGraph(leiden));
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}