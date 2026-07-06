package com.booktracker.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link NotificationEntity}.
 *
 * <p>All three query methods use explicit {@code @Query} to control:
 * <ul>
 *   <li>Counting: parameterized by {@code userId} — scoped to recipient (T-09-13)</li>
 *   <li>Listing: JOIN FETCH actor so {@link NotificationEntity#getActor()} is loaded
 *       for {@link NotificationDto#actorDisplayName} resolution without N+1</li>
 *   <li>Mark-all-read: {@code @Modifying} UPDATE filtered by {@code user.id = :userId}
 *       and {@code isRead = false} (T-09-15 — only caller's rows updated)</li>
 * </ul>
 *
 * <p><strong>Security note (T-09-13, T-09-15):</strong>
 * All query methods accept {@code userId} from the service layer which sources it
 * from {@code @AuthenticationPrincipal UserEntity.getId()} — never from request params.
 */
public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    /**
     * Count unread notifications for a user.
     *
     * <p>Used by {@code GET /api/notifications/unread-count}.
     * Parameterized by {@code userId} from JWT (T-09-13).
     *
     * @param userId the recipient's UUID (from @AuthenticationPrincipal)
     * @return number of is_read=false rows for this user
     */
    @Query("SELECT COUNT(n) FROM NotificationEntity n WHERE n.user.id = :userId AND n.isRead = false")
    long countByUserIdAndIsReadFalse(@Param("userId") UUID userId);

    /**
     * List all notifications for a user, newest first, with actor JOIN FETCHed.
     *
     * <p>JOIN FETCH on {@code n.actor} prevents N+1 when building {@link NotificationDto}
     * (actor.displayName is required for the DTO).
     * Ordered by {@code n.createdAt DESC} — matches the {@code notifications_user_read_idx} index.
     *
     * @param userId   the recipient's UUID (from @AuthenticationPrincipal)
     * @param pageable page/size for the notification list
     * @return paginated notifications ordered by createdAt DESC
     */
    @Query(
        value = "SELECT n FROM NotificationEntity n JOIN FETCH n.actor " +
                "WHERE n.user.id = :userId ORDER BY n.createdAt DESC",
        countQuery = "SELECT COUNT(n) FROM NotificationEntity n WHERE n.user.id = :userId"
    )
    Page<NotificationEntity> findByUserIdOrderByCreatedAtDesc(
            @Param("userId") UUID userId, Pageable pageable);

    /**
     * Bulk-update all unread notifications for a user to is_read=true.
     *
     * <p>{@code @Modifying} is required for UPDATE/DELETE JPQL.
     * Filtered by {@code n.user.id = :userId} — only the caller's rows are updated (T-09-15).
     * Filtered by {@code n.isRead = false} — idempotent (already-read rows are not touched).
     *
     * @param userId the recipient's UUID (from @AuthenticationPrincipal — T-09-15)
     */
    @Modifying
    @Query("UPDATE NotificationEntity n SET n.isRead = true " +
           "WHERE n.user.id = :userId AND n.isRead = false")
    void markAllReadForUser(@Param("userId") UUID userId);
}
