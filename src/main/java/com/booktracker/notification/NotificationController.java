package com.booktracker.notification;

import com.booktracker.user.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the notification domain (NOTIF-03).
 *
 * <p>Three endpoints — no class-level {@code @RequestMapping} (individual method mappings carry full paths):
 * <ul>
 *   <li>{@code GET  /api/notifications}             — paginated notification list, newest first</li>
 *   <li>{@code GET  /api/notifications/unread-count} — unread badge count</li>
 *   <li>{@code POST /api/notifications/read-all}     — mark all as read → 204</li>
 * </ul>
 *
 * <p><strong>Security (T-09-13, T-09-15):</strong>
 * All endpoints extract identity from {@code @AuthenticationPrincipal UserEntity currentUser} —
 * no {@code userId} is accepted from the request path or query string. This is the IDOR
 * mitigation required by the plan's threat model.
 *
 * <p><strong>Authentication:</strong>
 * The existing {@code anyRequest().authenticated()} rule in {@code SecurityConfig} covers all
 * {@code /api/**} paths. No SecurityConfig changes are needed for these endpoints.
 *
 * <p><strong>No SimpMessagingTemplate injection:</strong>
 * Push delivery is not part of this plan (Plan 09-04 adds it to {@code NotificationService}).
 */
@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * GET /api/notifications — paginated notification list for the authenticated user.
     *
     * <p>Returns 200 OK + {@code Page<NotificationDto>} ordered by createdAt DESC.
     * Empty page when the user has no notifications.
     * Identity is from {@code @AuthenticationPrincipal} (T-09-13 — never from query param).
     *
     * @param pageable    page/size (default page=0, size=20)
     * @param currentUser the authenticated user (from JWT principal)
     * @return paginated notification DTOs newest first
     */
    @GetMapping("/api/notifications")
    public Page<NotificationDto> listNotifications(
            Pageable pageable,
            @AuthenticationPrincipal UserEntity currentUser) {
        return notificationService.list(currentUser, pageable);
    }

    /**
     * GET /api/notifications/unread-count — returns the caller's unread notification count.
     *
     * <p>Returns 200 OK + {@code {"count": N}}.
     * Used by the frontend AppHeader bell badge.
     * Identity is from {@code @AuthenticationPrincipal} (T-09-13).
     *
     * @param currentUser the authenticated user (from JWT principal)
     * @return UnreadCountResponse containing the count
     */
    @GetMapping("/api/notifications/unread-count")
    public UnreadCountResponse getUnreadCount(
            @AuthenticationPrincipal UserEntity currentUser) {
        long count = notificationService.getUnreadCount(currentUser);
        return new UnreadCountResponse(count);
    }

    /**
     * POST /api/notifications/read-all — mark all the caller's notifications as read.
     *
     * <p>Returns 204 No Content on success (idempotent).
     * Only the authenticated user's own rows are updated (T-09-15).
     * Identity is from {@code @AuthenticationPrincipal} — never from request body.
     *
     * @param currentUser the authenticated user (from JWT principal)
     */
    @PostMapping("/api/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(
            @AuthenticationPrincipal UserEntity currentUser) {
        notificationService.markAllRead(currentUser);
    }

    /**
     * Small response record for the unread-count endpoint.
     * Serializes as {@code {"count": N}} — matches the frontend expectation.
     */
    public record UnreadCountResponse(long count) {}
}
