package com.booktracker.social;

import java.time.OffsetDateTime;

/**
 * Response DTO for friend request operations.
 *
 * <p>Returned by {@code POST /api/friend-requests}, {@code PUT .../accept},
 * {@code PUT .../reject}, and {@code GET /api/friend-requests/pending-received}.
 *
 * <p>All identity fields ({@code id}, {@code requesterId}, {@code recipientId}) are Strings
 * (UUID serialized as string) to avoid frontend UUID parsing issues.
 *
 * @param id                   UUID of the friend request row
 * @param requesterId          UUID of the user who sent the request
 * @param recipientId          UUID of the user who received the request
 * @param requesterDisplayName display name of the requester (for pending-received widget)
 * @param status               current status: PENDING, ACCEPTED, REJECTED, or CANCELLED
 * @param createdAt            when the request was created
 * @param requesterPhotoUrl    Cloudinary HTTPS URL for the requester's profile photo; null when no photo is set
 */
public record FriendRequestDto(
        String id,
        String requesterId,
        String recipientId,
        String requesterDisplayName,
        String status,
        OffsetDateTime createdAt,
        String requesterPhotoUrl
) {}
