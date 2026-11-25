package com.example.social.server.controller;

import com.example.social.server.entity.User;
import com.example.social.server.entity.UserPrivate;
import com.example.social.server.exception.ForbiddenException;
import com.example.social.server.exception.TooManyRequestsException;
import com.example.social.server.exception.UnauthorizedException;
import com.example.social.server.repository.UserPrivateRepository;
import com.example.social.server.repository.UserRepository;
import com.example.social.server.security.JwtTokenProvider;
import com.example.social.server.security.LoginAttemptService;
import com.example.social.server.service.UserService;
import com.example.social.shared.dto.LoginRequestDto;
import com.example.social.shared.dto.LoginResponseDto;
import com.example.social.shared.dto.RegisterRequestDto;
import com.example.social.shared.dto.RegisterResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final UserPrivateRepository userPrivateRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptService loginAttempts;

    /**
     * Хеш випадкового пароля, з яким звіряється введений пароль, коли логіна не існує.
     * Перевірка bcrypt триває ~0.1 с; без неї відповідь для вигаданого логіна приходила б помітно швидше,
     * і за часом відповіді можна було б з'ясувати, які логіни зареєстровано.
     */
    private final String dummyPasswordHash;

    private static final String WRONG_CREDENTIALS = "Неправильний логін або пароль";

    public AuthController(UserService userService,
                          UserRepository userRepository,
                          UserPrivateRepository userPrivateRepository,
                          PasswordEncoder passwordEncoder,
                          JwtTokenProvider jwtTokenProvider,
                          LoginAttemptService loginAttempts) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.userPrivateRepository = userPrivateRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginAttempts = loginAttempts;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
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
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request,
                                                  HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        loginAttempts.blockedFor(request.getUsername(), ip).ifPresent(wait -> {
            throw new TooManyRequestsException("Забагато невдалих спроб входу. Спробуйте через "
                    + minutes(wait) + " хв.");
        });

        User user = userRepository.findByUsername(request.getUsername()).orElse(null);
        UserPrivate userPrivate = user == null ? null : userPrivateRepository.findByUser(user).orElse(null);

        // Пароль перевіряється завжди — і для неіснуючого логіна (з підставним хешем),
        // щоб час відповіді не видавав, чи такий акаунт є
        String hash = userPrivate != null ? userPrivate.getPasswordHash() : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), hash);

        if (userPrivate == null || !passwordMatches) {
            loginAttempts.recordFailure(request.getUsername(), ip);
            throw new UnauthorizedException(WRONG_CREDENTIALS);
        }
        loginAttempts.recordSuccess(request.getUsername());

        if (userPrivate.isBlocked()) {
            String reason = userPrivate.getBlockedReason();
            throw new ForbiddenException(reason == null ? "Акаунт заблоковано" : "Акаунт заблоковано: " + reason);
        }

        String token = jwtTokenProvider.generateToken(
                user.getUserId(), user.getUsername(), userPrivate.getTokenVersion());

        return ResponseEntity.ok(new LoginResponseDto(
                token, user.getUserId(), user.getUsername(), userPrivate.getRole().name()));
    }

    /** Округлення вгору: «через 0 хв» не показуємо. */
    private static long minutes(Duration wait) {
        return Math.max(1, (wait.getSeconds() + 59) / 60);
    }
}