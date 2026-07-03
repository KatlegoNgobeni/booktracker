package com.booktracker.social;

/**
 * Represents the friendship relationship between the current user and a user search result.
 *
 * <p>Used in {@link UserSearchResultDto} to drive the friend request button state (D-06):
 * <ul>
 *   <li>{@code NONE}             → "Add Friend" button</li>
 *   <li>{@code PENDING_SENT}     → "Request Sent" button (cancel action)</li>
 *   <li>{@code PENDING_RECEIVED} → "Accept / Reject" buttons</li>
 *   <li>{@code ACCEPTED}         → "Friends" label (no action)</li>
 * </ul>
 *
 * <p>Computed by {@link FriendRequestService#searchUsers} in a single bulk query per search
 * page — no N+1 (RESEARCH Open Question 2).
 */
public enum FriendStatus {
    NONE,
    PENDING_SENT,
    PENDING_RECEIVED,
    ACCEPTED
}
