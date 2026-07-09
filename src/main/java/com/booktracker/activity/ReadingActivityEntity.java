package com.booktracker.activity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code reading_activity} table — one row per user per
 * active reading day. Powers streak computation (STATS-01/02) and idempotent
 * activity recording (STATS-03).
 *
 * <p>Column mapping (from V7__reading_streaks.sql):
 * <ul>
 *   <li>{@code user_id}       uuid (PK part) → {@link ReadingActivityId#getUserId()}</li>
 *   <li>{@code activity_date} date (PK part) → {@link ReadingActivityId#getActivityDate()}</li>
 * </ul>
 *
 * <p><strong>Repository anchor only:</strong> this entity exists so
 * {@link ReadingActivityRepository} can extend {@code JpaRepository}. There is
 * deliberately no {@code @ManyToOne} to {@code UserEntity} — all writes go
 * through the native {@code INSERT ... ON CONFLICT DO NOTHING} query (Hibernate
 * {@code save()} cannot express {@code ON CONFLICT}), and reads return plain
 * dates. The FK with {@code ON DELETE CASCADE} lives in the schema.
 */
@Entity
@Table(name = "reading_activity")
public class ReadingActivityEntity {

    @EmbeddedId
    private ReadingActivityId id;

    /** JPA requires a no-arg constructor. */
    protected ReadingActivityEntity() {
    }

    public ReadingActivityEntity(ReadingActivityId id) {
        this.id = id;
    }

    public ReadingActivityId getId() {
        return id;
    }
}
