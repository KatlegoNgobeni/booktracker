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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the {@code friend_requests} table (V3 migration).
 *
 * <p>Represents a single friend request in a four-state lifecycle:
 * PENDING → ACCEPTED / REJECTED / CANCELLED.
 *
 * <p>Column mapping (from V3__friend_requests.sql):
 * <ul>
 *   <li>{@code id}           uuid PK  → {@link UUID} via {@code GenerationType.UUID} (Hibernate 6)</li>
 *   <li>{@code requester_id} uuid FK  → {@link UserEntity} via {@code @ManyToOne(LAZY)}</li>
 *   <li>{@code recipient_id} uuid FK  → {@link UserEntity} via {@code @ManyToOne(LAZY)}</li>
 *   <li>{@code status}       varchar  → plain {@code String} (not {@code @Enumerated}) matching V3 CHECK constraint</li>
 *   <li>{@code created_at}   timestamptz → {@code OffsetDateTime} set via {@code @PrePersist}</li>
 *   <li>{@code updated_at}   timestamptz → {@code OffsetDateTime} set via {@code @PrePersist} + {@code @PreUpdate}</li>
 * </ul>
 *
 * <p><strong>@ManyToOne LAZY pattern (mirrors FollowEntity):</strong>
 * Both {@code requester} and {@code recipient} use {@code FetchType.LAZY}. JPQL queries in
 * {@link FriendRequestRepository} use JOIN FETCH where the referenced user data is needed.
 *
 * <p><strong>String status (not @Enumerated):</strong>
 * The V3 {@code friend_requests_status_chk} constraint enforces valid values at DB level.
 * Storing as a plain String avoids ordinal-vs-string confusion and makes JPQL literals
 * ({@code fr.status = 'PENDING'}) directly match stored values.
 *
 * <p><strong>@PreUpdate fires correctly (RESEARCH Pitfall 4):</strong>
 * {@link FriendRequestService} always does {@code findById → modify status → save()}, ensuring
 * Hibernate detects a dirty managed entity and fires {@code @PreUpdate}.
 *
 * <p><strong>Security:</strong> Identity is always from JWT via
 * {@code @AuthenticationPrincipal UserEntity} — no userId from request body (T-09-03).
 */
@Entity
@Table(name = "friend_requests")
public class FriendRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /**
     * The user who initiated the friend request — FK to {@code users.id}.
     * LAZY so the user is not loaded unless explicitly accessed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", nullable = false)
    private UserEntity requester;

    /**
     * The user receiving the friend request — FK to {@code users.id}.
     * LAZY so the user is not loaded unless explicitly accessed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private UserEntity recipient;

    /**
     * Status string — one of PENDING, ACCEPTED, REJECTED, CANCELLED.
     * Stored as VARCHAR; validated by V3 DB check constraint.
     * Defaults to "PENDING" at persist time.
     */
    @Column(nullable = false)
    private String status = "PENDING";

    /** Immutable — set at insert time via {@code @PrePersist}. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Mutable — updated by {@code @PreUpdate} on every dirty write. */
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Sets both {@code createdAt} and {@code updatedAt} before the first persist.
     * Only {@code @PrePersist} sets {@code createdAt} — it is marked {@code updatable=false}.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = OffsetDateTime.now();
    }

    /**
     * Updates {@code updatedAt} on every dirty write.
     * Fires correctly when {@code FriendRequestService} follows the
     * {@code findById → modify → save()} sequence (RESEARCH Pitfall 4).
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
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

    public UserEntity getRequester() {
        return requester;
    }

    public void setRequester(UserEntity requester) {
        this.requester = requester;
    }

    public UserEntity getRecipient() {
        return recipient;
    }

    public void setRecipient(UserEntity recipient) {
        this.recipient = recipient;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
