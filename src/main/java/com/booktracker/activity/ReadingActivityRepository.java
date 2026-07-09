package com.booktracker.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ReadingActivityEntity}.
 *
 * <p>Both methods are native queries with named {@code @Param} binding —
 * no string concatenation into SQL (T-12-01).
 *
 * <p><strong>Security note (T-12-01, T-12-03):</strong>
 * {@code userId} is always sourced from {@code @AuthenticationPrincipal
 * UserEntity.getId()} in the service layer — never from request params.
 * {@code activity_date} is always server-assigned {@code LocalDate.now()} at
 * the call site — never client-supplied (anti-backfill, T-12-03): a client
 * must not be able to forge historical streak days.
 */
public interface ReadingActivityRepository
        extends JpaRepository<ReadingActivityEntity, ReadingActivityId> {

    /**
     * Record reading activity for a user on a given day (STATS-03).
     *
     * <p>Requires an active transaction ({@code @Modifying} native insert) —
     * call sites in ShelfService are {@code @Transactional}.
     *
     * @param userId the authenticated user's UUID (from @AuthenticationPrincipal)
     * @param date   server-assigned {@code LocalDate.now()} — never client-supplied
     */
    @Modifying
    @Query(value = "INSERT INTO reading_activity (user_id, activity_date) " +
                   "VALUES (:userId, :date)", nativeQuery = true)
    void recordActivity(@Param("userId") UUID userId, @Param("date") LocalDate date);

    /**
     * All distinct activity dates for a user, newest first (STATS-01/02).
     *
     * <p>Dates are inherently distinct — the composite PK {@code (user_id,
     * activity_date)} permits at most one row per user per day. The DESC order
     * feeds streak computation directly (scan from most recent day backwards).
     *
     * @param userId the authenticated user's UUID (from @AuthenticationPrincipal)
     * @return activity dates ordered {@code activity_date DESC}
     */
    @Query(value = "SELECT activity_date FROM reading_activity " +
                   "WHERE user_id = :userId ORDER BY activity_date DESC", nativeQuery = true)
    List<LocalDate> findActivityDatesDesc(@Param("userId") UUID userId);
}
