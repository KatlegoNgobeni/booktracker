package com.booktracker.social;

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
 * JPA entity for the {@code follows} table — represents a directed follow relationship
 * between two users.
 *
 * <p>Column mapping (from V1__initial_schema.sql):
 * <ul>
 *   <li>{@code id}          uuid PK        → {@link UUID} via {@code GenerationType.UUID} (Hibernate 6)</li>
 *   <li>{@code follower_id} uuid FK        → {@link UserEntity} via {@code @ManyToOne(LAZY)}</li>
 *   <li>{@code followee_id} uuid FK        → {@link UserEntity} via {@code @ManyToOne(LAZY)}</li>
 *   <li>{@code created_at}  timestamptz    → {@code OffsetDateTime} (immutable, set via @PrePersist)</li>
 * </ul>
 *
 * <p><strong>@ManyToOne pattern (RESEARCH Pattern 1, Pitfall 4):</strong>
 * Both {@code follower} and {@code followee} use {@code @ManyToOne(FetchType.LAZY)} rather than
 * plain UUID columns. This enables JPQL navigation ({@code f.follower.id}, {@code f.followee.id})
 * in FollowRepository queries without loading the full UserEntity on every row.
 *
 * <p><strong>Self-follow prevention:</strong> V1 DB has {@code follows_no_self_follow check
 * (follower_id <> followee_id)} as the safety net. Service-layer check provides a friendlier
 * 400 message before the constraint fires (Pitfall 6).
 *
 * <p><strong>Duplicate follow prevention:</strong> V1 DB has {@code follows_pair_uq unique
 * (follower_id, followee_id)}. Caught as {@link org.springframework.dao.DataIntegrityViolationException}
 * in {@link SocialService#follow} and re-thrown as 409 (Pitfall 1).
 */
@Entity
@Table(name = "follows")
public class FollowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /**
     * The user initiating the follow — FK to {@code users.id}.
     * LAZY so the entity is not loaded unless explicitly accessed.
     * Enables JPQL navigation: {@code f.follower.id} in FollowRepository queries.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private UserEntity follower;

    /**
     * The user being followed — FK to {@code users.id}.
     * LAZY so the entity is not loaded unless explicitly accessed.
     * Enables JPQL navigation: {@code f.followee.id} in FollowRepository queries.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "followee_id", nullable = false)
    private UserEntity followee;

    /** Immutable — set at insert time via {@code @PrePersist}. */
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

    public UserEntity getFollower() {
        return follower;
    }

    public void setFollower(UserEntity follower) {
        this.follower = follower;
    }

    public UserEntity getFollowee() {
        return followee;
    }

    public void setFollowee(UserEntity followee) {
        this.followee = followee;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
