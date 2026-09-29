package com.example.social.server.controller;

import com.example.social.server.security.AuthenticatedUser;
import com.example.social.server.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/api/search")
    public ResponseEntity<?> search(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                    @RequestParam("q") String query) {
        Long currentUserId = currentUser != null ? currentUser.getUserId() : null;
        return ResponseEntity.ok(searchService.search(query, currentUserId));
    }
}