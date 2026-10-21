package com.example.social.server.security;

import com.example.social.server.entity.UserRole;
import com.example.social.server.repository.UserPrivateRepository;
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
            String token = authHeader.substring(7);

            if (jwtTokenProvider.isValid(token)) {
                String username = jwtTokenProvider.getUsername(token);
                Long userId = jwtTokenProvider.getUserId(token);

                // Роль читається з бази при кожному запиті, а не з токена:
                // так зміна ролі адміністратором діє одразу, без повторного входу.
                Optional<UserRole> role = userPrivateRepository.findRoleByUserId(userId);

                // Акаунт видалено — токен більше нічого не дає
                if (role.isPresent()) {
                    AuthenticatedUser principal = new AuthenticatedUser(userId, username, role.get());

                    // Spring Security очікує права у форматі ROLE_ADMIN / ROLE_USER
                    var authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + role.get().name().toUpperCase()));

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
