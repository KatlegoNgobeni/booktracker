package com.booktracker.notification;

import java.time.OffsetDateTime;

/**
 * Read-only DTO for a persisted notification.
 *
 * <p>Fields are aligned with RESEARCH RESOLVED Open Question 3: display text is computed
 * on the frontend from {@code type} + {@code actorDisplayName} — no denormalized text column
 * in the DB. The frontend uses {@code type} to select the correct label template and fills
 * in {@code actorDisplayName} as the subject.
 *
 * <p>All UUID fields are serialized as {@code String} to remain consistent with the project's
 * REST convention of using String UUIDs in JSON (avoids Jackson UUID→String confusion).
 *
 * @param id               notification UUID
 * @param type             one of: FRIEND_REQUEST, FRIEND_ACCEPTED, FRIEND_FINISHED_BOOK, REVIEW_LIKED
 * @param actorId          UUID of the user who caused the notification (as String)
 * @param actorDisplayName display name of the actor — frontend uses this for human-readable text
 * @param entityId         UUID of the related resource (entry id, request id, etc.) — may be null
 * @param isRead           true if the user has marked all notifications as read since this was created
 * @param createdAt        ISO-8601 timestamp of when the notification was created
 */
public record NotificationDto(
        String id,
        String type,
        String actorId,
        String actorDisplayName,
        String entityId,
        boolean isRead,
        OffsetDateTime createdAt
) {}
