package com.booktracker.goal;

import com.booktracker.user.UserEntity;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the yearly reading goal — exposes {@code PUT /goal} and
 * {@code GET /goal} under the {@code /api} context-path.
 *
 * <p>All endpoints require a valid JWT (covered by {@code anyRequest().authenticated()}
 * in {@code SecurityConfig} — no changes to the security config needed for Phase 5).
 *
 * <p><strong>@AuthenticationPrincipal:</strong> Uses {@code UserEntity} directly (not
 * {@code UserDetails}) because {@code JwtAuthenticationFilter} populates the
 * {@code SecurityContext} with a {@code UserEntity} instance — same pattern as
 * {@code ShelfController}.
 *
 * <p><strong>Year derivation (D-01, D-03):</strong> No year is accepted from the client —
 * it is always derived server-side by {@link GoalService} from
 * {@code LocalDate.now().getYear()}.
 *
 * <p><strong>404 handling (D-02):</strong> {@code GET /goal} delegates to
 * {@link GoalService#getGoalForCurrentYear} which throws
 * {@code ResponseStatusException(NOT_FOUND)} when no goal is set. The existing
 * {@code GlobalExceptionHandler} handles this transparently.
 */
@RestController
@RequestMapping("/goal")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    /**
     * PUT /goal — set or update the current year's reading goal.
     *
     * <p>Returns 200 OK + {@link GoalDto} on success.
     * Returns 400 Bad Request if {@code targetCount} is null or negative (@NotNull @Min(0)).
     *
     * @param req  JSON body with non-negative {@code targetCount}
     * @param user the authenticated user (injected from JWT principal)
     * @return the persisted (or updated) goal DTO
     */
    @PutMapping
    public GoalDto setGoal(
            @Valid @RequestBody SetGoalRequest req,
            @AuthenticationPrincipal UserEntity user) {
        return goalService.upsertGoal(req.getTargetCount(), user);
    }

    /**
     * GET /goal — retrieve the current year's reading goal.
     *
     * <p>Returns 200 OK + {@link GoalDto} if a goal has been set.
     * Returns 404 Not Found if no goal has been set for the current year (D-02).
     * The 404 is thrown by {@link GoalService#getGoalForCurrentYear} as a
     * {@code ResponseStatusException} and surfaced by {@code GlobalExceptionHandler}.
     *
     * @param user the authenticated user
     * @return the goal DTO for the current calendar year
     */
    @GetMapping
    public GoalDto getGoal(@AuthenticationPrincipal UserEntity user) {
        return goalService.getGoalForCurrentYear(user);
    }
}
