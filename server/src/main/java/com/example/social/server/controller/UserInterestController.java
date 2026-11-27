package com.example.social.server.controller;

import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.MyFeedInsightsService;
import com.example.social.server.service.UserInterestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Обрані теми (холодний старт стрічки) і сторінка «Моя стрічка» поточного користувача. */
@RestController
public class UserInterestController {

    private final UserInterestService interestService;
    private final MyFeedInsightsService insightsService;

    public UserInterestController(UserInterestService interestService, MyFeedInsightsService insightsService) {
        this.interestService = interestService;
        this.insightsService = insightsService;
    }

    public record InterestsRequest(List<String> tags) {
    }

    @GetMapping("/api/users/me/interests")
    public ResponseEntity<?> getInterests(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(interestService.get(currentUser.getUserId()));
    }

    /** Порожній список — «пропустити»: теми не обрано, і вибір більше не з'являється сам. */
    @PutMapping("/api/users/me/interests")
    public ResponseEntity<?> updateInterests(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                             @RequestBody InterestsRequest request) {
        return ResponseEntity.ok(interestService.update(currentUser.getUserId(), request.tags()));
    }

    /** «Моя стрічка»: моя спільнота, склад стрічки, теми й настрій вподобаного порівняно з мережею. */
    @GetMapping("/api/users/me/insights")
    public ResponseEntity<?> getInsights(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(insightsService.forUser(currentUser.getUserId()));
    }
}
