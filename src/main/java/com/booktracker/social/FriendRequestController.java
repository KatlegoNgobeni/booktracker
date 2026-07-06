package com.booktracker.social;

import com.booktracker.user.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for friend request endpoints (D-01, DISC-02, DISC-03).
 *
 * <p>All five endpoints are under {@code /api/friend-requests} (no class-level
 * {@code @RequestMapping} — full paths per method, following the 07-01 and 08-01 convention).
 *
 * <p><strong>Security (threat model):</strong>
 * All endpoints require a valid JWT (covered by {@code anyRequest().authenticated()} in
 * {@code SecurityConfig} — no changes needed). The actor identity is ALWAYS
 * {@code @AuthenticationPrincipal UserEntity currentUser} (T-09-03 — never from path or body).
 *
 * <p><strong>Ownership enforcement (T-09-01, T-09-02):</strong>
 * Accept/reject are guarded to the recipient; cancel is guarded to the requester.
 * These checks are in {@link FriendRequestService} — the controller only delegates.
 *
 * <p><strong>@PathVariable UUID:</strong>
 * Spring MVC auto-rejects malformed UUIDs with 400 via type binding (same pattern as
 * {@code SocialController}).
 */
@RestController
public class FriendRequestController {

    private final FriendRequestService friendRequestService;

    public FriendRequestController(FriendRequestService friendRequestService) {
        this.friendRequestService = friendRequestService;
    }

    /**
     * POST /api/friend-requests — send a friend request to the specified user.
     *
     * <p>Returns 201 Created + {@link FriendRequestDto} with status PENDING on success.
     * Returns 400 if attempting to send to self.
     * Returns 404 if recipient user does not exist.
     * Returns 409 if an active (PENDING or ACCEPTED) relationship already exists.
     *
     * <p>The requester is always {@code currentUser} from {@code @AuthenticationPrincipal} —
     * NEVER from the request body (T-09-03 IDOR mitigation).
     *
     * @param dto         request body — only recipientId (target, not actor)
     * @param currentUser authenticated requester (from JWT principal)
     * @return 201 Created + FriendRequestDto
     */
    @PostMapping("/api/friend-requests")
    public ResponseEntity<FriendRequestDto> sendRequest(
            @RequestBody SendFriendRequestDto dto,
            @AuthenticationPrincipal UserEntity currentUser) {
        FriendRequestDto result = friendRequestService.sendRequest(dto.recipientId(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * PUT /api/friend-requests/{id}/accept — accept a pending friend request.
     *
     * <p>Returns 200 OK + {@link FriendRequestDto} with status ACCEPTED on success.
     * Returns 404 if the request does not exist.
     * Returns 403 if the current user is not the recipient (T-09-01 IDOR guard).
     *
     * @param id          UUID of the friend request row
     * @param currentUser authenticated user (from JWT principal)
     * @return updated FriendRequestDto with status ACCEPTED
     */
    @PutMapping("/api/friend-requests/{id}/accept")
    public FriendRequestDto acceptRequest(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserEntity currentUser) {
        return friendRequestService.acceptRequest(id, currentUser);
    }

    /**
     * PUT /api/friend-requests/{id}/reject — reject a pending friend request.
     *
     * <p>Returns 200 OK + {@link FriendRequestDto} with status REJECTED on success.
     * Returns 404 if the request does not exist.
     * Returns 403 if the current user is not the recipient (T-09-01 IDOR guard).
     *
     * @param id          UUID of the friend request row
     * @param currentUser authenticated user (from JWT principal)
     * @return updated FriendRequestDto with status REJECTED
     */
    @PutMapping("/api/friend-requests/{id}/reject")
    public FriendRequestDto rejectRequest(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserEntity currentUser) {
        return friendRequestService.rejectRequest(id, currentUser);
    }

    /**
     * DELETE /api/friend-requests/{id} — cancel a pending friend request (requester only).
     *
     * <p>Deletes the row (allows re-send later).
     * Returns 204 No Content on success.
     * Returns 404 if the request does not exist.
     * Returns 403 if the current user is not the requester (T-09-02 IDOR guard).
     *
     * @param id          UUID of the friend request row
     * @param currentUser authenticated user (from JWT principal)
     */
    @DeleteMapping("/api/friend-requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelRequest(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserEntity currentUser) {
        friendRequestService.cancelRequest(id, currentUser);
    }

    /**
     * GET /api/friend-requests/pending-received — list pending incoming friend requests
     * for the current user (Feed discovery widget, DISC-03, D-09).
     *
     * <p>Returns 200 OK + list of {@link FriendRequestDto} with status PENDING, ordered
     * by createdAt DESC. Returns an empty list when no pending requests exist.
     *
     * <p>The recipient is always the current user from JWT — no userId from HTTP (T-09-03).
     *
     * @param currentUser authenticated user (from JWT principal)
     * @return list of pending received friend requests
     */
    @GetMapping("/api/friend-requests/pending-received")
    public List<FriendRequestDto> getPendingReceived(
            @AuthenticationPrincipal UserEntity currentUser) {
        return friendRequestService.getPendingReceived(currentUser);
    }
}
