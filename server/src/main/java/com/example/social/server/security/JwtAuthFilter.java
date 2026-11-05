package com.example.social.server.security;

import com.example.social.server.repository.UserPrivateRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserPrivateRepository userPrivateRepository;

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider,
                         UserPrivateRepository userPrivateRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userPrivateRepository = userPrivateRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authenticate(authHeader.substring(7));
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Якщо токен дійсний, а користувач не заблокований і версія токена збігається з поточною,
     * кладе користувача в SecurityContext. Інакше запит іде далі як анонімний і отримає 403.
     */
    private void authenticate(String token) {
        Claims claims;
        try {
            claims = jwtTokenProvider.parseClaims(token);   // токен розбирається один раз
        } catch (JwtException | IllegalArgumentException e) {
            return;
        }

        Long userId = claims.get("userId", Long.class);
        if (userId == null) {
            return;
        }

        // Стан читається з бази при кожному запиті: зміна ролі, блокування
        // і скидання пароля адміністратором діють одразу, без повторного входу.
        Optional<AuthState> state = userPrivateRepository.findAuthStateByUserId(userId);
        if (state.isEmpty() || !state.get().allows(JwtTokenProvider.tokenVersion(claims))) {
            return;
        }

        AuthenticatedUser principal = new AuthenticatedUser(userId, claims.getSubject(), state.get().role());
        var authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + state.get().role().name().toUpperCase()));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}