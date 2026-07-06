package com.booktracker.notification;

/**
 * Enum of notification event types — must match the V5 CHECK constraint values exactly.
 *
 * <p>Stored as {@code String} in the {@code notifications.type} column (VARCHAR).
 * {@link NotificationEntity} stores {@code type.name()} not the enum itself —
 * avoids {@code @Enumerated(EnumType.STRING)} dependency on Hibernate ordinal mapping.
 *
 * <p>Values match the V5 migration CHECK constraint:
 * {@code type in ('FRIEND_REQUEST','FRIEND_ACCEPTED','FRIEND_FINISHED_BOOK','REVIEW_LIKED')}.
 */
public enum NotificationType {
    /** Sent to the recipient when a friend request is received. */
    FRIEND_REQUEST,
    /** Sent to the requester when their friend request is accepted. */
    FRIEND_ACCEPTED,
    /** Sent to all friends when a friend finishes reading a book (adds to READ shelf). */
    FRIEND_FINISHED_BOOK,
    /** Sent to the review owner when another user likes their review entry. */
    REVIEW_LIKED
}
