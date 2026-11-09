package com.example.social.server.security;

import com.example.social.server.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JWT: вміст токена, підпис, строк дії, версія токенів і правило допуску AuthState. */
class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-that-is-longer-than-32-bytes";
    private static final String OTHER_SECRET = "another-secret-key-also-longer-than-32-bytes";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 3_600_000);

    @Test
    void tokenCarriesUserIdUsernameAndVersion() {
        String token = provider.generateToken(83L, "medoko", 2);

        Claims claims = provider.parseClaims(token);
        assertEquals("medoko", claims.getSubject());
        assertEquals(83L, claims.get("userId", Long.class));
        assertEquals(2, JwtTokenProvider.tokenVersion(claims));
        assertTrue(provider.isValid(token));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreign = new JwtTokenProvider(OTHER_SECRET, 3_600_000).generateToken(83L, "medoko", 0);

        assertFalse(provider.isValid(foreign));
        assertThrows(JwtException.class, () -> provider.parseClaims(foreign));
    }

    @Test
    void tamperedPayloadIsRejected() {
        String[] parts = provider.generateToken(89L, "user", 0).split("\\.");
        // Підміняємо вміст (userId 83 — спроба видати себе за адміна), підпис лишається старий
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"medoko\",\"userId\":83,\"ver\":0}".getBytes(StandardCharsets.UTF_8));

        assertFalse(provider.isValid(parts[0] + "." + forgedPayload + "." + parts[2]));
    }

    @Test
    void expiredTokenIsRejected() {
        String expired = new JwtTokenProvider(SECRET, -1_000).generateToken(83L, "medoko", 0);

        assertFalse(provider.isValid(expired));
        assertThrows(ExpiredJwtException.class, () -> provider.parseClaims(expired));
    }

    @Test
    void shortSecretFailsAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider("too-short", 3_600_000));
    }

    @Test
    void tokenIssuedBeforeVersioningCountsAsVersionZero() {
        String legacy = Jwts.builder()
                .subject("medoko")
                .claim("userId", 83L)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertEquals(0, JwtTokenProvider.tokenVersion(provider.parseClaims(legacy)));
    }

    // ===== AuthState: кого фільтр пускає далі =====

    @Test
    void activeUserWithCurrentTokenVersionIsAllowed() {
        assertTrue(new AuthState(UserRole.user, false, 0).allows(0));
    }

    @Test
    void blockedUserIsRejectedEvenWithValidToken() {
        assertFalse(new AuthState(UserRole.admin, true, 0).allows(0));
    }

    @Test
    void tokenFromBeforePasswordResetIsRejected() {
        // після скидання пароля версія в БД стала 1, а в старому токені лишилась 0
        assertFalse(new AuthState(UserRole.user, false, 1).allows(0));
    }
}