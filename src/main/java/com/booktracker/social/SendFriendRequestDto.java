package com.booktracker.social;

import java.util.UUID;

/**
 * Request body DTO for {@code POST /api/friend-requests}.
 *
 * <p>Only the recipient's UUID is provided by the caller — the requester identity
 * is ALWAYS taken from {@code @AuthenticationPrincipal UserEntity currentUser} in the
 * controller (T-09-03: Spoofing mitigation — identity from JWT, never request body).
 *
 * @param recipientId UUID of the user to whom the friend request is sent
 */
public record SendFriendRequestDto(UUID recipientId) {}
