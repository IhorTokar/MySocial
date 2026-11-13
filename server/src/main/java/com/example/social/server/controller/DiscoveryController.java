package com.example.social.server.controller;

import com.example.social.server.exception.BadRequestException;
import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.DiscoveryService;
import com.example.social.shared.dto.SuggestedUserDto;
import com.example.social.shared.dto.TrendingTagDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Бічна панель клієнта: гарячі теми й кого почитати. */
@RestController
@RequestMapping("/api/discover")
public class DiscoveryController {

    private static final int MAX_LIMIT = 20;

    private final DiscoveryService discoveryService;

    public DiscoveryController(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping("/trends")
    public ResponseEntity<List<TrendingTagDto>> trends(@RequestParam(name = "limit", defaultValue = "8") int limit) {
        return ResponseEntity.ok(discoveryService.trendingTags(checkLimit(limit)));
    }

    @GetMapping("/people")
    public ResponseEntity<List<SuggestedUserDto>> people(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                         @RequestParam(name = "limit", defaultValue = "5") int limit) {
        return ResponseEntity.ok(discoveryService.suggestedUsers(currentUser.getUserId(), checkLimit(limit)));
    }

    private static int checkLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BadRequestException("limit має бути від 1 до " + MAX_LIMIT);
        }
        return limit;
    }
}