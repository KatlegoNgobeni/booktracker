package com.booktracker.social;

import com.booktracker.shelf.UserBookEntity;
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
 * JPA entity for the {@code review_likes} table — represents one user's like on a shelf entry.
 *
 * <p>Column mapping (from V4__review_likes.sql):
 * <ul>
 *   <li>{@code id}          uuid PK        → {@link UUID} via {@code GenerationType.UUID} (Hibernate 6)</li>
 *   <li>{@code user_id}     uuid FK        → {@link UserEntity} via {@code @ManyToOne(LAZY)}</li>
 *   <li>{@code entry_id}    uuid FK        → {@link UserBookEntity} via {@code @ManyToOne(LAZY)}</li>
 *   <li>{@code created_at}  timestamptz    → {@code OffsetDateTime} (immutable, set via @PrePersist)</li>
 * </ul>
 *
 * <p><strong>Immutable entity:</strong> Likes cannot be edited, only created or deleted.
 * There is NO {@code @PreUpdate} and no {@code updatedAt} column — intentional.
 * The {@code @PrePersist} sets {@code createdAt} only.
 *
 * <p><strong>@ManyToOne pattern:</strong>
 * Both {@code user} and {@code entry} use {@code @ManyToOne(FetchType.LAZY)} to enable
 * JPQL navigation without loading full entities on every query.
 *
 * <p><strong>Duplicate prevention:</strong> V4 DB has {@code review_likes_pair_uq unique
 * (user_id, entry_id)}. Caught as {@link org.springframework.dao.DataIntegrityViolationException}
 * in {@link LikeService#likeReview} and re-thrown as 409.
 *
 * <p><strong>Security (T-09-07):</strong> The {@code user} is always set from
 * {@code @AuthenticationPrincipal} in the controller — never from the request body or path.
 */
@Entity
@Table(name = "review_likes")
public class LikeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /**
     * The user who liked the entry — FK to {@code users.id}.
     * LAZY so the entity is not loaded unless explicitly accessed.
     * Set from JWT principal only (T-09-07 IDOR mitigation).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /**
     * The shelf entry (user_books row) that was liked — FK to {@code user_books.id}.
     * LAZY fetch; ON DELETE CASCADE ensures likes are removed when the entry is deleted.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false)
    private UserBookEntity entry;

    /** Immutable — set at insert time via {@code @PrePersist}. No @PreUpdate (likes are immutable). */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Sets {@code createdAt} before the first persist. No @PreUpdate — likes are immutable. */
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

    public UserBookEntity getEntry() {
        return entry;
    }

    public void setEntry(UserBookEntity entry) {
        this.entry = entry;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
