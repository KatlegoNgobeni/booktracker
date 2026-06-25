package com.booktracker.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link GoalEntity}.
 *
 * <p>The key query is {@link #findByUserIdAndYear} — used by both the upsert path
 * (GoalService.upsertGoal) and the read path (GoalService.getGoalForCurrentYear) to
 * look up the current year's goal for a given user.
 *
 * <p>The JPQL query navigates the {@code g.user.id} association rather than joining
 * on the {@code user} object directly — this keeps the query portable and avoids
 * loading the full {@link com.booktracker.user.UserEntity} just to compare IDs.
 */
public interface GoalRepository extends JpaRepository<GoalEntity, UUID> {

    /**
     * Find the goal for a specific user and calendar year.
     *
     * <p>Backs both the upsert check (does a goal already exist?) and the read path
     * (return the current year's goal, or 404 if absent — D-02, D-04).
     *
     * @param userId the authenticated user's UUID
     * @param year   the calendar year (e.g. 2026)
     * @return the goal if one has been set for that user+year, or empty Optional
     */
    @Query("SELECT g FROM GoalEntity g WHERE g.user.id = :userId AND g.year = :year")
    Optional<GoalEntity> findByUserIdAndYear(@Param("userId") UUID userId, @Param("year") int year);
}
