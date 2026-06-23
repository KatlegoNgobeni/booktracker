package com.booktracker.security;

import com.booktracker.user.UserEntity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationFilter — verifies:
 * - Missing header → passes through (chain called, SecurityContext empty)
 * - Valid token → SecurityContext populated
 * - Expired/malformed token → passes through (no exception thrown, D-09/Pitfall 5)
 * - filterChain.doFilter called on EVERY path
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private UserEntity sampleUser;
    private UUID sampleId;

    @BeforeEach
    void setUp() {
        // Clear security context before each test
        SecurityContextHolder.clearContext();

        sampleId = UUID.randomUUID();
        sampleUser = new UserEntity();
        sampleUser.setId(sampleId);
        sampleUser.setEmail("filter@example.com");
        sampleUser.setPasswordHash("$2a$10$hash");
        sampleUser.setDisplayName("Filter User");
    }

    @Test
    void missingAuthHeader_passesThrough_noAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        // Chain must be called (D-09: always continue)
        verify(filterChain).doFilter(request, response);
        // SecurityContext must remain empty
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void nonBearerHeader_passesThrough_noAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validToken_populatesSecurityContext() throws Exception {
        String validToken = "valid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + validToken);
        when(jwtUtil.extractSubject(validToken)).thenReturn(sampleId.toString());
        when(jwtUtil.isTokenExpired(validToken)).thenReturn(false);
        when(userDetailsService.loadUserByUsername(sampleId.toString())).thenReturn(sampleUser);

        filter.doFilterInternal(request, response, filterChain);

        // Chain must be called even when token is valid
        verify(filterChain).doFilter(request, response);
        // SecurityContext must be populated
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo(sampleId.toString());
    }

    @Test
    void malformedToken_doesNotThrow_passesThrough() throws Exception {
        String badToken = "not-a-jwt";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + badToken);
        when(jwtUtil.extractSubject(badToken)).thenThrow(new io.jsonwebtoken.MalformedJwtException("malformed"));

        // Must NOT throw — D-09 / Pitfall 5
        filter.doFilterInternal(request, response, filterChain);

        // Chain must still be called
        verify(filterChain).doFilter(request, response);
        // SecurityContext must remain empty
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void expiredToken_doesNotThrow_passesThrough() throws Exception {
        String expiredToken = "expired.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + expiredToken);
        when(jwtUtil.extractSubject(expiredToken)).thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "expired"));

        // Must NOT throw — D-09 / Pitfall 5
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
