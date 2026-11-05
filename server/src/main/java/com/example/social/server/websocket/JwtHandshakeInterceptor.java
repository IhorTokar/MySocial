package com.example.social.server.websocket;

import com.example.social.server.repository.UserPrivateRepository;
import com.example.social.server.security.AuthState;
import com.example.social.server.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserPrivateRepository userPrivateRepository;

    public JwtHandshakeInterceptor(JwtTokenProvider jwtTokenProvider,
                                   UserPrivateRepository userPrivateRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userPrivateRepository = userPrivateRepository;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String authHeader = request.getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Claims claims;
        try {
            claims = jwtTokenProvider.parseClaims(authHeader.substring(7));
        } catch (JwtException | IllegalArgumentException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Long userId = claims.get("userId", Long.class);
        Optional<AuthState> state = userId == null ? Optional.empty()
                : userPrivateRepository.findAuthStateByUserId(userId);
        if (state.isEmpty() || !state.get().allows(JwtTokenProvider.tokenVersion(claims))) {
            response.setStatusCode(HttpStatus.FORBIDDEN);   // заблокований або токен відкликано
            return false;
        }

        // userId беремо ТІЛЬКИ з токена, ніколи з тіла повідомлення
        attributes.put("userId", userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // нічого
    }
}