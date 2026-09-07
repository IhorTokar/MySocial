package com.example.social.server.controller;

import com.example.social.server.entity.User;
import com.example.social.server.service.UserService;
import com.example.social.shared.dto.RegisterRequestDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequestDto request) {
        try {
            User user = userService.registerUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword()
            );
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(Map.of(
                            "userId", user.getUserId(),
                            "username", user.getUsername(),
                            "uid", user.getUid()
                    ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}