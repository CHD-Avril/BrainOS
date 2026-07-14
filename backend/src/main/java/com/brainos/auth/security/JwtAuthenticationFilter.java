package com.brainos.auth.security;

import com.brainos.auth.domain.UserAccount;
import com.brainos.auth.domain.UserRepository;
import com.brainos.auth.domain.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final UserRepository users;

    public JwtAuthenticationFilter(JwtDecoder jwtDecoder, UserRepository users) {
        this.jwtDecoder = jwtDecoder;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null
                && authorization.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(authorization.substring(BEARER_PREFIX.length()));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            long userId = Long.parseLong(jwt.getSubject());
            String roleClaim = jwt.getClaimAsString("role");
            if (roleClaim == null) {
                return;
            }
            UserRole claimedRole = UserRole.valueOf(roleClaim);
            users.findById(userId)
                    .filter(UserAccount::isEnabled)
                    .filter(user -> user.role() == claimedRole)
                    .ifPresent(this::setAuthentication);
        } catch (JwtException | IllegalArgumentException exception) {
            // Invalid credentials deliberately leave the request anonymous.
        }
    }

    private void setAuthentication(UserAccount user) {
        UserPrincipal principal = new UserPrincipal(user.id(), user.username(), user.role());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
