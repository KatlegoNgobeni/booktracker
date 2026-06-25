package com.booktracker.goal;

import com.booktracker.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the {@code goals} table — maps one yearly reading goal per user per year.
 *
 * <p>Column mapping (from V1__initial_schema.sql):
 * <ul>
 *   <li>{@code id}           uuid PK   → {@link UUID} via {@code GenerationType.UUID} (Hibernate 6)</li>
 *   <li>{@code user_id}      uuid FK   → {@link UserEntity} via {@code @ManyToOne(LAZY)}</li>
 *   <li>{@code year}         integer   → {@code Integer year}</li>
 *   <li>{@code target_count} integer   → {@code Integer targetCount} (check >= 0 in DB)</li>
 *   <li>{@code created_at}   timestamptz → {@code OffsetDateTime createdAt} (immutable)</li>
 * </ul>
 *
 * <p><strong>Namespace:</strong> {@code jakarta.persistence.*} — Spring Boot 3.x requires Jakarta EE
 * (CLAUDE.md §Version Gotchas).
 *
 * <p><strong>UUID strategy:</strong> {@code GenerationType.UUID} (Hibernate 6).
 * The {@code "uuid2"} strategy from Hibernate 5 is REMOVED and must not be used.
 *
 * <p><strong>No new Flyway migration:</strong> The {@code goals} table is already in
 * {@code V1__initial_schema.sql} — no V2+ migration file is needed for Phase 5.
 */
@Entity
@Table(name = "goals")
public class GoalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /**
     * The user who owns this goal — FK to {@code users.id}.
     * Loaded lazily — no join needed outside of query context.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /** Calendar year for this goal (e.g. 2026). Derived server-side via LocalDate.now().getYear(). */
    @Column(nullable = false)
    private Integer year;

    /**
     * Target number of books to read for {@link #year}.
     * DB has {@code check (target_count >= 0)} — {@code @Min(0)} on the request DTO provides
     * the friendly 400 response before reaching the DB constraint.
     */
    @Column(name = "target_count", nullable = false)
    private Integer targetCount;

    /** Immutable — defaulted at insert time via {@code @PrePersist}. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Sets {@code createdAt} before the first persist if not already set. */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    // ----------------------------------------------------------------
    // Getters and setters
    // ----------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(Integer targetCount) {
        this.targetCount = targetCount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
