package com.booktracker.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for user profile endpoints.
 *
 * <p>Mapped under {@code /users} (becomes {@code /api/users} via context-path).
 * All endpoints require a valid JWT (any request not in the permit list is protected
 * by {@code SecurityConfig.anyRequest().authenticated()}).
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /api/users/me — returns the authenticated user's own profile (AUTH-03, D-10).
     *
     * <p>{@code @AuthenticationPrincipal UserDetails} is injected by Spring Security from
     * the {@code SecurityContext} — populated by {@code JwtAuthenticationFilter} on each
     * authenticated request.
     *
     * <p>The principal's username is the user's UUID string (D-06:
     * {@code UserEntity.getUsername()} returns {@code id.toString()}).
     *
     * @param userDetails the authenticated principal (never null on a protected endpoint)
     * @return DTO with id, email, displayName, createdAt — no password field (D-10)
     */
    @GetMapping("/me")
    public UserResponseDto me(@AuthenticationPrincipal UserDetails userDetails) {
        // D-06: username = UUID string (from UserEntity.getUsername())
        UUID userId = UUID.fromString(userDetails.getUsername());
        return userService.getUserById(userId);
    }
}
