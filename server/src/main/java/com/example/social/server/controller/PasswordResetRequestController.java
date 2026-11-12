package com.example.social.server.controller;

import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.PasswordResetRequestService;
import com.example.social.shared.dto.AdminPasswordResetDto;
import com.example.social.shared.dto.PasswordResetRequestAdminDto;
import com.example.social.shared.dto.PasswordResetRequestDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * «Забув пароль». POST /api/auth/… — без токена (шлях відкритий у SecurityConfig),
 * /api/admin/… — лише для адміністратора.
 */
@RestController
@RequestMapping("/api")
public class PasswordResetRequestController {

    private final PasswordResetRequestService service;

    public PasswordResetRequestController(PasswordResetRequestService service) {
        this.service = service;
    }

    @PostMapping("/auth/password-reset-requests")
    public ResponseEntity<Map<String, String>> submit(@Valid @RequestBody PasswordResetRequestDto dto) {
        service.submit(dto);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("message",
                "Якщо такий акаунт існує, адміністратор розгляне заявку і зв'яжеться з вами."));
    }

    /** @param status pending — лише нові (за замовчуванням), all — усі */
    @GetMapping("/admin/password-requests")
    public ResponseEntity<List<PasswordResetRequestAdminDto>> list(
            @RequestParam(name = "status", defaultValue = "pending") String status) {
        return ResponseEntity.ok(service.list(!"all".equals(status)));
    }

    @GetMapping("/admin/password-requests/count")
    public ResponseEntity<Map<String, Long>> pendingCount() {
        return ResponseEntity.ok(Map.of("pending", service.pendingCount()));
    }

    @PostMapping("/admin/password-requests/{requestId}/resolve")
    public ResponseEntity<PasswordResetRequestAdminDto> resolve(@AuthenticationPrincipal AuthenticatedUser admin,
                                                                @PathVariable("requestId") long requestId,
                                                                @Valid @RequestBody AdminPasswordResetDto dto) {
        return ResponseEntity.ok(service.resolve(requestId, dto.newPassword(), admin.getUserId()));
    }

    @PostMapping("/admin/password-requests/{requestId}/reject")
    public ResponseEntity<PasswordResetRequestAdminDto> reject(@AuthenticationPrincipal AuthenticatedUser admin,
                                                               @PathVariable("requestId") long requestId) {
        return ResponseEntity.ok(service.reject(requestId, admin.getUserId()));
    }
}