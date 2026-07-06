package com.booktracker.notification;

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
 * JPA entity for the {@code notifications} table (V5 migration).
 *
 * <p>Column mapping:
 * <ul>
 *   <li>{@code id}         uuid PK default gen_random_uuid()  → {@link UUID} via {@code GenerationType.UUID}</li>
 *   <li>{@code user_id}    uuid FK                             → {@link UserEntity} via {@code @ManyToOne(LAZY)}</li>
 *   <li>{@code type}       varchar NOT NULL                    → {@code String} (NotificationType.name())</li>
 *   <li>{@code actor_id}   uuid FK                             → {@link UserEntity} via {@code @ManyToOne(LAZY)}</li>
 *   <li>{@code entity_id}  uuid nullable                       → {@link UUID} (related resource id, e.g. friend request id)</li>
 *   <li>{@code is_read}    boolean NOT NULL default false       → {@code boolean}</li>
 *   <li>{@code created_at} timestamptz NOT NULL default now()  → {@link OffsetDateTime} via {@code @PrePersist}</li>
 * </ul>
 *
 * <p><strong>Type stored as String:</strong> {@code type} is stored as {@code type.name()} String,
 * consistent with the V5 CHECK constraint values. This avoids {@code @Enumerated} and matches
 * the project convention used by {@code FriendRequestEntity.status}.
 *
 * <p><strong>@ManyToOne LAZY:</strong> Both {@code user} (the recipient) and {@code actor}
 * (the triggering user) use LAZY loading. The repository uses JOIN FETCH for queries that
 * need actor display name resolution (T-09-13 — actor name needed for NotificationDto).
 *
 * <p><strong>Pitfall 6 prevention:</strong> {@code NotificationEntity} does NOT import
 * {@code ShelfService} — cross-domain circular dependency is avoided by passing only
 * {@code entityId} (not shelf data) through the notification payload.
 */
@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /**
     * The notification recipient — FK to {@code users.id} (ON DELETE CASCADE).
     * Notifications are private to this user (T-09-13).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /**
     * Notification type stored as String — matches V5 CHECK constraint values.
     * Values: FRIEND_REQUEST, FRIEND_ACCEPTED, FRIEND_FINISHED_BOOK, REVIEW_LIKED.
     */
    @Column(nullable = false)
    private String type;

    /**
     * The user who triggered the notification (e.g., the one who sent the friend request).
     * FK to {@code users.id}. JOIN FETCHed in queries that need the actor's display name.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private UserEntity actor;

    /**
     * The related resource UUID — nullable.
     * Examples: friend request id for FRIEND_REQUEST/FRIEND_ACCEPTED,
     * user_books id for FRIEND_FINISHED_BOOK, user_books id for REVIEW_LIKED.
     * The frontend uses this to deep-link to the relevant resource.
     */
    @Column(name = "entity_id")
    private UUID entityId;

    /** False until the user opens the notification inbox and calls POST /api/notifications/read-all. */
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    /**
     * Immutable — set once at insert time via {@code @PrePersist}.
     * Matches V5 default: {@code now()}.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Sets {@code createdAt} to now() before the first persist.
     * {@code isRead} defaults to {@code false} via the field initializer.
     */
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public UserEntity getActor() {
        return actor;
    }

    public void setActor(UserEntity actor) {
        this.actor = actor;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
