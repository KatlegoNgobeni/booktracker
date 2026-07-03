package com.booktracker.social;

/**
 * Lifecycle states for a {@link FriendRequestEntity}.
 *
 * <p>These names match the V3 migration's {@code friend_requests_status_chk} constraint:
 * {@code check (status in ('PENDING','ACCEPTED','REJECTED','CANCELLED'))}.
 * The entity stores the status as a plain {@code String} (not {@code @Enumerated}) so that
 * the JPQL literals in {@link FriendRequestRepository} match the stored values verbatim.
 *
 * <p>State machine:
 * <ul>
 *   <li>{@code PENDING} — request sent; neither accepted nor declined yet</li>
 *   <li>{@code ACCEPTED} — recipient accepted; both users see each other's activity in the feed</li>
 *   <li>{@code REJECTED} — recipient declined; row kept for deduplication purposes</li>
 *   <li>{@code CANCELLED} — requester cancelled; row is actually deleted (allows re-send)</li>
 * </ul>
 */
public enum FriendRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED
}
