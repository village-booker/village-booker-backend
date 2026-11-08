package com.villagebooker.security;

import com.villagebooker.repo.user.UserRepo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

// Reads "Authorization: Bearer <token>" and, if the token is valid, marks the request as authenticated.
// An invalid or missing token is not an error here: the request continues unauthenticated and
// protected endpoints answer 401 (SecurityProblemHandler).
// One query by id per request with a token: a blocked user is stopped at once (403 USER_BLOCKED, also on public
// routes), not when the token expires; a deleted user counts as unauthenticated. The role still comes from the token.
// Not a @Component on purpose: it is added only to the security filter chain (SecurityConfig).
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepo userRepo;
    private final SecurityProblemHandler problemHandler;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<AuthUser> user = jwtService.parse(header.substring(BEARER_PREFIX.length()));
            if (user.isPresent()) {
                Optional<Boolean> blocked = userRepo.findBlockedById(user.get().id());
                if (blocked.orElse(false)) {
                    problemHandler.userBlocked(request, response);
                    return;
                }
                if (blocked.isPresent()) {
                    authenticate(request, user.get());
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private static void authenticate(HttpServletRequest request, AuthUser user) {
        var authentication = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
