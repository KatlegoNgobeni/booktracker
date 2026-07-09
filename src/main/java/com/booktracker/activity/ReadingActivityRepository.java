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
     * Idempotently record reading activity for a user on a given day (STATS-03).
     *
     * <p>{@code ON CONFLICT} (do-nothing) against the composite PK
     * {@code (user_id, activity_date)} makes the insert atomic and race-free
     * (T-12-02) — no check-then-insert, no duplicate-key 500 under concurrent
     * same-day requests.
     *
     * <p>Requires an active transaction ({@code @Modifying} native insert) —
     * call sites in ShelfService are {@code @Transactional}.
     *
     * @param userId the authenticated user's UUID (from @AuthenticationPrincipal)
     * @param date   server-assigned {@code LocalDate.now()} — never client-supplied
     */
    @Modifying
    @Query(value = "INSERT INTO reading_activity (user_id, activity_date) " +
                   "VALUES (:userId, :date) ON CONFLICT DO NOTHING", nativeQuery = true)
    void recordActivity(@Param("userId") UUID userId, @Param("date") LocalDate date);

    /**
     * All distinct activity dates for a user, newest first (STATS-01/02).
     *
     * <p>Dates are inherently distinct — the composite PK {@code (user_id,
     * activity_date)} permits at most one row per user per day. The DESC order
     * feeds streak computation directly (scan from most recent day backwards).
     *
     * <p><strong>Why JPQL, not native:</strong> a native {@code SELECT
     * activity_date} returns {@code java.sql.Date} scalars, which Spring Data
     * cannot convert to {@code List<LocalDate>} (ConversionFailedException —
     * observed against real PostgreSQL). JPQL over the {@code LocalDate}-mapped
     * embeddable attribute yields {@code LocalDate} directly. The write path
     * stays native because Hibernate cannot express {@code ON CONFLICT}.
     *
     * @param userId the authenticated user's UUID (from @AuthenticationPrincipal)
     * @return activity dates ordered {@code activityDate DESC}
     */
    @Query("SELECT ra.id.activityDate FROM ReadingActivityEntity ra " +
           "WHERE ra.id.userId = :userId ORDER BY ra.id.activityDate DESC")
    List<LocalDate> findActivityDatesDesc(@Param("userId") UUID userId);
}
