package com.booktracker.social;

/**
 * Response DTO for a single user search result (DISC-01, D-06).
 *
 * <p>Each result card shows: display name + friend request button state.
 * No email or private shelf data is exposed (T-09-05 — Info Disclosure accepted/low).
 *
 * @param id           UUID of the matched user (String)
 * @param displayName  display name of the matched user
 * @param friendStatus current friendship state between the searcher and this user
 *                     (NONE, PENDING_SENT, PENDING_RECEIVED, or ACCEPTED)
 * @param requestId    UUID of the friend request row when {@code friendStatus} is
 *                     PENDING_SENT or PENDING_RECEIVED (so the frontend can accept/cancel);
 *                     null when NONE or ACCEPTED
 */
public record UserSearchResultDto(
        String id,
        String displayName,
        FriendStatus friendStatus,
        String requestId
) {}
