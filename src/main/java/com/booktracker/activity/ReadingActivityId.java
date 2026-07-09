package com.booktracker.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for the {@code reading_activity} table:
 * {@code (user_id, activity_date)}.
 *
 * <p>Deliberate exception to the project's UUID-PK convention (documented in
 * {@code V7__reading_streaks.sql}): {@code reading_activity} is a keyless fact
 * table whose row identity IS the (user, day) pair — the composite key is
 * simultaneously the {@code ON CONFLICT} idempotency anchor (STATS-03) and a
 * free per-user index.
 *
 * <p>Implements {@link Serializable} and value-based {@code equals}/{@code hashCode}
 * over both fields — required by the JPA composite-key contract.
 */
@Embeddable
public class ReadingActivityId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "activity_date")
    private LocalDate activityDate;

    /** JPA requires a no-arg constructor. */
    protected ReadingActivityId() {
    }

    public ReadingActivityId(UUID userId, LocalDate activityDate) {
        this.userId = userId;
        this.activityDate = activityDate;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReadingActivityId other)) {
            return false;
        }
        return Objects.equals(userId, other.userId)
                && Objects.equals(activityDate, other.activityDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, activityDate);
    }
}
