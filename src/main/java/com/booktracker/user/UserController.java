package com.booktracker.user;

import com.booktracker.social.FriendRequestService;
import com.booktracker.social.UserSearchResultDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for user profile and search endpoints.
 *
 * <p>Mapped under {@code /api/users}. All endpoints require a valid JWT
 * (covered by {@code SecurityConfig.anyRequest().authenticated()}).
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final FriendRequestService friendRequestService;

    public UserController(UserService userService, FriendRequestService friendRequestService) {
        this.userService = userService;
        this.friendRequestService = friendRequestService;
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

    /**
     * GET /api/users/search?q=name — search for users by display name (DISC-01, D-05).
     *
     * <p>Returns a paginated list of {@link UserSearchResultDto} for users whose
     * {@code displayName} contains {@code q} (case-insensitive), excluding the current user.
     * Each result includes a {@code friendStatus} derived from the friend_requests table
     * in a single bulk query — no N+1 (RESEARCH Open Question 2).
     *
     * <p><strong>T-09-04 (SQL injection):</strong> The {@code q} parameter is passed to
     * JPQL as a bind parameter ({@code :query}) — never concatenated.
     *
     * <p>The current user's identity comes from {@code @AuthenticationPrincipal UserEntity}
     * — never from a request parameter (T-09-03 Spoofing mitigation).
     *
     * @param q           required search string (case-insensitive display name fragment)
     * @param pageable    page/size for the results (default page=0, size=20)
     * @param currentUser the authenticated user (from JWT principal)
     * @return paginated UserSearchResultDto list ordered by displayName ASC
     */
    @GetMapping("/search")
    public Page<UserSearchResultDto> searchUsers(
            @RequestParam String q,
            Pageable pageable,
            @AuthenticationPrincipal UserEntity currentUser) {
        return friendRequestService.searchUsers(q, pageable, currentUser);
    }
}
