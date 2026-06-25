package com.booktracker.goal;

import com.booktracker.user.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Business logic for the yearly reading goal: upsert and read for the current calendar year.
 *
 * <p><strong>Upsert logic (D-04):</strong> The {@code goals} table has a {@code (user_id, year)}
 * unique constraint. Rather than relying on a DB ON CONFLICT, the service uses an explicit
 * find-or-create pattern within a {@code @Transactional} method:
 * <ol>
 *   <li>Look up any existing goal for the authenticated user and current year.</li>
 *   <li>If present: update {@code targetCount} on the existing entity.</li>
 *   <li>If absent: create a new {@link GoalEntity} with user, year, and targetCount.</li>
 *   <li>Save with plain {@code save()} (not {@code saveAndFlush} — no constraint race
 *       because we always check first, per RESEARCH Pitfall 6).</li>
 * </ol>
 *
 * <p><strong>Year derivation (D-01, D-03):</strong> The current year is always derived
 * server-side from {@code LocalDate.now().getYear()} — no year is accepted from the client.
 * Both endpoints are scoped to the authenticated user via {@code @AuthenticationPrincipal UserEntity}
 * in {@link GoalController} — no userId in request body or path (T-05-02).
 *
 * <p><strong>Transactional import:</strong> Uses
 * {@code org.springframework.transaction.annotation.Transactional} (Spring), NOT
 * {@code jakarta.transaction.Transactional} — enforced by CLAUDE.md.
 */
@Service
public class GoalService {

    private final GoalRepository goalRepository;

    public GoalService(GoalRepository goalRepository) {
        this.goalRepository = goalRepository;
    }

    /**
     * Upsert the reading goal for the authenticated user and the current calendar year.
     *
     * <p>If a goal already exists for this user+year, {@code targetCount} is updated on the
     * existing row (no new row, no duplicate). If no goal exists yet, a new row is created.
     *
     * @param targetCount the desired target (>= 0, validated upstream by {@link SetGoalRequest})
     * @param user        the authenticated user (from {@code @AuthenticationPrincipal})
     * @return DTO representing the persisted goal (id, targetCount, year)
     */
    @Transactional
    public GoalDto upsertGoal(int targetCount, UserEntity user) {
        int currentYear = LocalDate.now().getYear();
        Optional<GoalEntity> existing =
                goalRepository.findByUserIdAndYear(user.getId(), currentYear);

        GoalEntity goal;
        if (existing.isPresent()) {
            // Update existing goal — same row, same year, new targetCount
            goal = existing.get();
            goal.setTargetCount(targetCount);
        } else {
            // Create new goal for this user + current year
            goal = new GoalEntity();
            goal.setUser(user);
            goal.setYear(currentYear);
            goal.setTargetCount(targetCount);
        }

        // Plain save() — no saveAndFlush() needed; we already checked for conflicts above
        // (RESEARCH Pitfall 6). Use the returned entity so the generated ID is available
        // immediately (relevant when creating a new entity before Hibernate assigns the UUID).
        GoalEntity saved = goalRepository.save(goal);
        return toDto(saved);
    }

    /**
     * Retrieve the reading goal for the authenticated user for the current calendar year.
     *
     * @param user the authenticated user (from {@code @AuthenticationPrincipal})
     * @return DTO representing the goal
     * @throws ResponseStatusException 404 if no goal has been set for this user+year (D-02)
     */
    public GoalDto getGoalForCurrentYear(UserEntity user) {
        int currentYear = LocalDate.now().getYear();
        return goalRepository.findByUserIdAndYear(user.getId(), currentYear)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No goal set for this year"));
    }

    /**
     * Map a {@link GoalEntity} to a {@link GoalDto}.
     */
    private GoalDto toDto(GoalEntity goal) {
        return new GoalDto(
                goal.getId().toString(),
                goal.getTargetCount(),
                goal.getYear()
        );
    }
}
