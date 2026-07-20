package com.booktracker.notification;

import com.booktracker.user.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Business logic for the notification domain (NOTIF-01 persistence, NOTIF-02 push, NOTIF-03 read API).
 *
 * <p><strong>Plan 09-04 augmentation:</strong>
 * {@link SimpMessagingTemplate} is now injected and used in {@link #createNotification} to push
 * the notification to the recipient's STOMP queue immediately after persisting.
 *
 * <p><strong>Security invariants:</strong>
 * <ul>
 *   <li>T-09-13: {@code getUnreadCount} and {@code list} are scoped to {@code currentUser.getId()}
 *       from {@code @AuthenticationPrincipal} — no userId accepted from request.</li>
 *   <li>T-09-15: {@code markAllRead} passes only {@code currentUser.getId()} to the UPDATE query
 *       — rows for other users are never updated.</li>
 * </ul>
 *
 * <p><strong>Pitfall 2 prevention (RESEARCH):</strong>
 * {@code convertAndSendToUser} is called with {@code recipient.getUsername()} as the user argument.
 * {@code UserEntity.getUsername()} returns {@code id.toString()} — the UUID string — which exactly
 * matches the principal name set by {@code JwtChannelInterceptor.accessor.setUser(auth)}.
 * Passing any other string (e.g. email) causes silent delivery failure.
 *
 * <p><strong>Pitfall 6 prevention (RESEARCH):</strong>
 * {@code NotificationService} does NOT inject {@code ShelfService} — avoids the
 * {@code NotificationService} ↔ {@code ShelfService} circular dependency. The
 * {@code FRIEND_FINISHED_BOOK} trigger in Plan 09-04 passes only {@code entityId} (UUID),
 * not a full shelf DTO.
 *
 * <p><strong>Push safety (RESEARCH Assumption A3):</strong>
 * {@code SimpMessagingTemplate.convertAndSendToUser()} is a safe no-op when the recipient is
 * not connected — Spring silently discards the message. No exception is thrown, so the
 * {@code @Transactional} method does not roll back when the recipient is offline.
 *
 * <p><strong>Transactional import:</strong> Uses {@code org.springframework.transaction.annotation.Transactional}
 * (Spring), NOT {@code jakarta.transaction.Transactional} — avoids the Jakarta namespace pitfall
 * from CLAUDE.md §Version Gotchas.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Constructor injection — {@link SimpMessagingTemplate} is available once
     * {@code spring-boot-starter-websocket} is on the classpath and {@code @EnableWebSocketMessageBroker}
     * is active in {@link com.booktracker.config.WebSocketConfig}.
     *
     * @param notificationRepository notification persistence store
     * @param messagingTemplate      STOMP messaging template for user-targeted push delivery
     */
    public NotificationService(NotificationRepository notificationRepository,
                               SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Persist a notification for the given recipient AND push it to their STOMP queue if connected.
     *
     * <p>Steps:
     * <ol>
     *   <li>Persist a {@link NotificationEntity} row (NOTIF-01)</li>
     *   <li>Build a {@link NotificationDto} from the saved entity</li>
     *   <li>Push via {@code messagingTemplate.convertAndSendToUser(recipient.getUsername(),
     *       "/queue/notifications", dto)} (NOTIF-02)</li>
     * </ol>
     *
     * <p><strong>RESEARCH Pitfall 2 (principal name):</strong>
     * {@code recipient.getUsername()} == {@code recipient.getId().toString()} — the UUID string.
     * This must exactly match {@code JwtChannelInterceptor.accessor.getUser().getName()}.
     *
     * <p><strong>RESEARCH Pitfall 6 (circular dependency):</strong>
     * This service does NOT import ShelfService. Callers pass the {@code entityId} (UUID) only.
     *
     * <p><strong>RESEARCH Pitfall anti-patterns (synchronous call):</strong>
     * Push is synchronous within the same {@code @Transactional} method — consistent with
     * RESEARCH Pattern 3 and RESOLVED Open Question 1. Calling from {@code @Async} or
     * {@code @EventListener} can cause IllegalStateException on detached entities.
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
        NotificationEntity saved = notificationRepository.save(entity);

        // NOTIF-02: Push to the recipient's STOMP queue if they are connected.
        // convertAndSendToUser is a safe no-op when recipient has no active STOMP session.
        // Routing: /user/{recipient.getUsername()}/queue/notifications
        NotificationDto dto = toDto(saved);
        messagingTemplate.convertAndSendToUser(
                recipient.getUsername(),    // UUID string — matches STOMP principal name (Pitfall 2)
                "/queue/notifications",     // destination without /user prefix (Spring adds it)
                dto
        );

        return saved;
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
                n.getCreatedAt(),
                n.getActor().getProfilePhotoUrl()
        );
    }
}
