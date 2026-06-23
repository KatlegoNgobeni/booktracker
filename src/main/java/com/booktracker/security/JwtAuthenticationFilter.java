package com.booktracker.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter — populates the Spring Security context from a valid Bearer token
 * on every inbound request (D-09, AUTH-04).
 *
 * <p>Implements {@link OncePerRequestFilter} to guarantee exactly one execution per request,
 * even in filter chains that could invoke a filter multiple times (e.g., with forward dispatches).
 *
 * <p><strong>Filter contract (D-09 / Pitfall 5):</strong>
 * <ul>
 *   <li>Valid Bearer token → extract UUID subject → load UserDetails → set SecurityContext</li>
 *   <li>Missing or non-Bearer Authorization header → pass through (no exception, no 401)</li>
 *   <li>Expired, malformed, or otherwise invalid token → catch {@link JwtException}, pass through
 *       (no exception thrown; SecurityContext stays empty; Spring Security returns 401 automatically
 *       for protected endpoints via the authorization filter)</li>
 *   <li>{@code filterChain.doFilter} is ALWAYS called, on every code path</li>
 * </ul>
 *
 * <p><strong>Jakarta namespace:</strong> Uses {@code jakarta.servlet.*} (Spring Boot 3.x / Jakarta EE 10).
 * Never use {@code javax.servlet.*} (Jakarta EE 8 — CLAUDE.md §Version Gotchas).
 *
 * <p>Registered in the Spring Security filter chain via
 * {@code SecurityConfig.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)}.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    /**
     * Constructor injection — avoids field-injection issues and makes dependencies
     * explicit for testing.
     */
    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Core filter logic — extracts and validates the JWT from the Authorization header.
     *
     * <p>{@code filterChain.doFilter} is called twice (via early return and final call)
     * to ensure the chain continues regardless of token presence or validity.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // D-09: Missing or non-Bearer header — do not throw; just pass through
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring(7);

            // jjwt 0.12.x: extractSubject parses and verifies the signature;
            // throws JwtException (ExpiredJwtException, MalformedJwtException, etc.) on failure
            String userUuid = jwtUtil.extractSubject(token);

            // Only populate SecurityContext if subject is present and context is not already authenticated
            if (userUuid != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // D-06: username = UUID string from sub claim
                UserDetails userDetails = userDetailsService.loadUserByUsername(userUuid);

                // Double-check expiry — extractSubject may succeed on a token that expiration
                // has not been fully validated yet on some jjwt versions
                if (!jwtUtil.isTokenExpired(token)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException e) {
            // D-09 / Pitfall 5: Invalid/expired/malformed/unsigned token — do NOT throw.
            // Leave SecurityContext empty; Spring Security will return 401 automatically
            // when the request hits a protected endpoint.
            // T-02-08: Forged/tampered/expired tokens are rejected here silently.
        }

        // D-09: ALWAYS continue the filter chain — whether token was valid or not
        filterChain.doFilter(request, response);
    }
}
