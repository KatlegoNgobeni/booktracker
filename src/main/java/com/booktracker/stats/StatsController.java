package com.booktracker.stats;

import com.booktracker.user.UserEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for computed reading analytics — exposes {@code GET /api/stats}
 * under the {@code /api} context-path.
 *
 * <p>No {@code SecurityConfig} change required — the endpoint is covered by
 * {@code anyRequest().authenticated()} already configured in Phase 2 (T-05-01).
 * Unauthenticated requests receive 401 Unauthorized from the JWT filter before
 * reaching this controller.
 *
 * <p><strong>{@code @AuthenticationPrincipal UserEntity}:</strong> Uses {@code UserEntity}
 * directly (not {@code UserDetails}) — the same pattern as {@code ShelfController} and
 * {@code GoalController}. The JWT filter populates the security context with a
 * {@code UserEntity} instance; injecting {@code UserEntity} directly avoids a redundant
 * DB lookup.
 *
 * <p><strong>Constructor injection</strong> — no {@code @Autowired} field injection (CLAUDE.md).
 *
 * <p><strong>No new Flyway migration</strong> — stats are computed on-the-fly (D-12).
 *
 * <p>Implements STATS-02: D-06 (single endpoint), D-07 (all-time + current-year in one call),
 * D-08 (full field set), T-05-01 (JWT enforcement), T-05-02 (IDOR: userId from principal,
 * never from request).
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * GET /stats — return computed reading analytics for the authenticated user.
     *
     * <p>Delegates entirely to {@link StatsService#getStats(UserEntity)} — no business
     * logic in this controller. The {@link StatsDto} is serialised by Jackson; null fields
     * are omitted via {@code @JsonInclude(NON_NULL)} on the DTO class.
     *
     * @param user the authenticated user (injected from JWT principal — never from request)
     * @return the computed analytics DTO
     */
    @GetMapping
    public StatsDto getStats(@AuthenticationPrincipal UserEntity user) {
        return statsService.getStats(user);
    }
}
