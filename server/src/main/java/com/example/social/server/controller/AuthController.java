package com.example.social.server.controller;

import com.example.social.server.entity.User;
import com.example.social.server.entity.UserPrivate;
import com.example.social.server.exception.ForbiddenException;
import com.example.social.server.exception.UnauthorizedException;
import com.example.social.server.repository.UserPrivateRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.server.security.JwtTokenProvider;
import com.example.social.server.service.UserService;
import com.example.social.shared.dto.LoginRequestDto;
import com.example.social.shared.dto.LoginResponseDto;
import com.example.social.shared.dto.RegisterRequestDto;
import com.example.social.shared.dto.RegisterResponseDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final UserPrivateRepository userPrivateRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(UserService userService,
                          UserRepository userRepository,
                          UserPrivateRepository userPrivateRepository,
                          PasswordEncoder passwordEncoder,
                          JwtTokenProvider jwtTokenProvider) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.userPrivateRepository = userPrivateRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        User user = userService.registerUser(
                request.getUsername(),
                request.getEmail(),
                request.getPassword()
        );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RegisterResponseDto(user.getUserId(), user.getUsername(), user.getUid()));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElse(null);

        if (user == null) {
            throw new UnauthorizedException("Invalid username or password");
        }

        UserPrivate userPrivate = userPrivateRepository.findByUser(user)
                .orElse(null);

        if (userPrivate.isBlocked()) {
            String reason = userPrivate.getBlockedReason();
            throw new ForbiddenException(reason == null ? "Акаунт заблоковано" : "Акаунт заблоковано: " + reason);
        }

        String token = jwtTokenProvider.generateToken(
                user.getUserId(), user.getUsername(), userPrivate.getTokenVersion());

        return ResponseEntity.ok(new LoginResponseDto(
                token, user.getUserId(), user.getUsername(), userPrivate.getRole().name()));
    }
}