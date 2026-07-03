package com.booktracker.notification;

import com.booktracker.user.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Business logic for the notification domain (NOTIF-01 persistence, NOTIF-03 read API).
 *
 * <p><strong>Plan 09-03 scope — persist-only:</strong>
 * This service persists notifications and serves the read/mark-read REST API.
 * WebSocket push ({@code SimpMessagingTemplate}) is NOT injected here — that is added
 * in Plan 09-04 which augments {@link #createNotification} to also push.
 *
 * <p><strong>Security invariants:</strong>
 * <ul>
 *   <li>T-09-13: {@code getUnreadCount} and {@code list} are scoped to {@code currentUser.getId()}
 *       from {@code @AuthenticationPrincipal} — no userId accepted from request.</li>
 *   <li>T-09-15: {@code markAllRead} passes only {@code currentUser.getId()} to the UPDATE query
 *       — rows for other users are never updated.</li>
 * </ul>
 *
 * <p><strong>Pitfall 6 prevention (RESEARCH):</strong>
 * {@code NotificationService} does NOT inject {@code ShelfService} — avoids the
 * {@code NotificationService} ↔ {@code ShelfService} circular dependency. The
 * {@code FRIEND_FINISHED_BOOK} trigger in Plan 09-04 passes only {@code entityId} (UUID),
 * not a full shelf DTO.
 *
 * <p><strong>Transactional import:</strong> Uses {@code org.springframework.transaction.annotation.Transactional}
 * (Spring), NOT {@code jakarta.transaction.Transactional} — avoids the Jakarta namespace pitfall
 * from CLAUDE.md §Version Gotchas.
 *
 * <p><strong>Constructor injection:</strong> Only {@link NotificationRepository} — no
 * {@code SimpMessagingTemplate} in this plan (added in Plan 09-04).
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Persist a notification for the given recipient.
     *
     * <p>Plan 09-03: persist-only — no WebSocket push. Plan 09-04 adds push by augmenting
     * this method with {@code SimpMessagingTemplate.convertAndSendToUser()}.
     *
     * <p>Called by FriendRequestService (Plan 09-04) and LikeService (Plan 09-04) when events occur.
     * The caller is responsible for passing the recipient and actor — this service never
     * queries other domains (Pitfall 6 prevention).
     *
     * @param recipient the user who should receive the notification
     * @param type      the notification type (stored as type.name())
     * @param actor     the user who triggered the event
     * @param entityId  the related resource UUID (nullable — e.g. friend request id, entry id)
     * @return the persisted {@link NotificationEntity}
     */
    @Transactional
    public NotificationEntity createNotification(UserEntity recipient, NotificationType type,
                                                  UserEntity actor, UUID entityId) {
        NotificationEntity entity = new NotificationEntity();
        entity.setUser(recipient);
        entity.setType(type.name());
        entity.setActor(actor);
        entity.setEntityId(entityId);
        // isRead defaults to false (field initializer + V5 column default)
        return notificationRepository.save(entity);
    }

    /**
     * Return the number of unread notifications for the authenticated user.
     *
     * <p>T-09-13: scoped to {@code currentUser.getId()} — never to a request parameter.
     *
     * @param currentUser the authenticated user (from @AuthenticationPrincipal)
     * @return count of is_read=false rows for this user
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(UserEntity currentUser) {
        return notificationRepository.countByUserIdAndIsReadFalse(currentUser.getId());
    }

    /**
     * Return the authenticated user's notifications, newest first.
     *
     * <p>T-09-13: scoped to {@code currentUser.getId()} — never to a request parameter.
     * Actor is JOIN FETCHed so {@code actorDisplayName} is available without N+1.
     *
     * @param currentUser the authenticated user (from @AuthenticationPrincipal)
     * @param pageable    page/size for the notification list
     * @return paginated {@link NotificationDto} list ordered by createdAt DESC
     */
    @Transactional(readOnly = true)
    public Page<NotificationDto> list(UserEntity currentUser, Pageable pageable) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(currentUser.getId(), pageable)
                .map(this::toDto);
    }

    /**
     * Mark all the authenticated user's unread notifications as read.
     *
     * <p>T-09-15: the UPDATE is filtered by {@code user.id = currentUser.getId()} and
     * {@code isRead = false} — rows belonging to other users are never touched.
     * Idempotent: calling this when all notifications are already read has no effect.
     *
     * @param currentUser the authenticated user (from @AuthenticationPrincipal)
     */
    @Transactional
    public void markAllRead(UserEntity currentUser) {
        notificationRepository.markAllReadForUser(currentUser.getId());
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    /**
     * Map a {@link NotificationEntity} to a {@link NotificationDto}.
     *
     * <p>{@code actor} must be loaded (not a lazy proxy) — ensured by JOIN FETCH in
     * {@link NotificationRepository#findByUserIdOrderByCreatedAtDesc}.
     */
    private NotificationDto toDto(NotificationEntity n) {
        return new NotificationDto(
                n.getId().toString(),
                n.getType(),
                n.getActor().getId().toString(),
                n.getActor().getDisplayName(),
                n.getEntityId() != null ? n.getEntityId().toString() : null,
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
