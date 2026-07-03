package com.booktracker.social;

import com.booktracker.user.UserEntity;
import com.booktracker.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Business logic for the friend-request state machine (D-01, DISC-02).
 *
 * <p><strong>Security invariants (threat model):</strong>
 * <ul>
 *   <li>T-09-01: Only the recipient may accept or reject — ownership checked via
 *       {@code findById → 404, recipient.id == currentUser.id → else 403}</li>
 *   <li>T-09-02: Only the requester may cancel — ownership checked via
 *       {@code findById → 404, requester.id == currentUser.id → else 403}</li>
 *   <li>T-09-03: Identity is always from {@code @AuthenticationPrincipal UserEntity} in the
 *       controller — recipientId from request body is only the TARGET, never the ACTOR</li>
 *   <li>T-09-06: Self-request rejected at service layer before any DB write (400)</li>
 * </ul>
 *
 * <p><strong>Transactional import:</strong> Uses {@code org.springframework.transaction.annotation.Transactional}
 * (Spring), NOT {@code jakarta.transaction.Transactional} — avoids version gotcha.
 *
 * <p><strong>saveAndFlush pattern (same as SocialService/ShelfService):</strong>
 * {@code saveAndFlush()} forces the SQL INSERT within the transaction so
 * {@link DataIntegrityViolationException} fires inside the try block (not at transaction
 * commit), allowing clean 409 mapping before the GlobalExceptionHandler runs.
 *
 * <p><strong>Bidirectional existence check (RESEARCH Pitfall 5):</strong>
 * {@link FriendRequestRepository#existsActiveRelationship} checks both directions —
 * the V3 unique constraint is directional; this service guard is not.
 */
@Service
public class FriendRequestService {

    private final FriendRequestRepository friendRequestRepository;
    private final UserRepository userRepository;

    public FriendRequestService(FriendRequestRepository friendRequestRepository,
                                UserRepository userRepository) {
        this.friendRequestRepository = friendRequestRepository;
        this.userRepository = userRepository;
    }

    /**
     * Send a friend request.
     *
     * <p>Steps:
     * <ol>
     *   <li>Self-request guard — 400 (T-09-06)</li>
     *   <li>Verify recipient exists — 404 if not</li>
     *   <li>Bidirectional active-relationship check — 409 if PENDING or ACCEPTED exists (Pitfall 5)</li>
     *   <li>Persist {@link FriendRequestEntity} via {@code saveAndFlush()} inside try/catch</li>
     *   <li>Catch {@link DataIntegrityViolationException} → 409 (pair_uq defense-in-depth)</li>
     * </ol>
     *
     * @param recipientId UUID of the recipient (from request body — target only, not actor)
     * @param currentUser authenticated requester (from @AuthenticationPrincipal — T-09-03)
     * @return {@link FriendRequestDto} for the new PENDING request
     * @throws ResponseStatusException 400 self-request; 404 unknown recipient; 409 active relationship exists
     */
    @Transactional
    public FriendRequestDto sendRequest(UUID recipientId, UserEntity currentUser) {
        // T-09-06: Self-request check
        if (recipientId.equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot send friend request to yourself");
        }

        // Verify recipient exists
        UserEntity recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Pitfall 5: Bidirectional active-relationship check (covers PENDING + ACCEPTED, both directions)
        if (friendRequestRepository.existsActiveRelationship(currentUser.getId(), recipientId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already connected or pending");
        }

        FriendRequestEntity entity = new FriendRequestEntity();
        entity.setRequester(currentUser);
        entity.setRecipient(recipient);
        entity.setStatus("PENDING");

        try {
            // saveAndFlush forces constraint check inside try block (same pattern as ShelfService / SocialService)
            entity = friendRequestRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            // friend_requests_pair_uq fired — defense-in-depth after the application check
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already connected or pending");
        }

        return toDto(entity);
    }

    /**
     * Accept a friend request.
     *
     * <p>Only the recipient may accept (T-09-01 — Elevation of Privilege mitigation).
     * The row is looked up by id (404 if missing), ownership checked against
     * {@code currentUser.getId()}, and the status is updated to ACCEPTED via
     * {@code findById → modify → save()} so {@code @PreUpdate} fires correctly (Pitfall 4).
     *
     * @param requestId   UUID of the friend request row
     * @param currentUser authenticated user (from @AuthenticationPrincipal)
     * @return updated {@link FriendRequestDto} with status ACCEPTED
     * @throws ResponseStatusException 404 if not found; 403 if not the recipient
     */
    @Transactional
    public FriendRequestDto acceptRequest(UUID requestId, UserEntity currentUser) {
        FriendRequestEntity entity = findById(requestId);

        // T-09-01: Only recipient may accept
        if (!entity.getRecipient().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to accept this request");
        }

        entity.setStatus("ACCEPTED");
        entity = friendRequestRepository.save(entity);
        return toDto(entity);
    }

    /**
     * Reject a friend request.
     *
     * <p>Only the recipient may reject (T-09-01 — Elevation of Privilege mitigation).
     *
     * @param requestId   UUID of the friend request row
     * @param currentUser authenticated user (from @AuthenticationPrincipal)
     * @return updated {@link FriendRequestDto} with status REJECTED
     * @throws ResponseStatusException 404 if not found; 403 if not the recipient
     */
    @Transactional
    public FriendRequestDto rejectRequest(UUID requestId, UserEntity currentUser) {
        FriendRequestEntity entity = findById(requestId);

        // T-09-01: Only recipient may reject
        if (!entity.getRecipient().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to reject this request");
        }

        entity.setStatus("REJECTED");
        entity = friendRequestRepository.save(entity);
        return toDto(entity);
    }

    /**
     * Cancel a friend request (requester cancels — deletes the row, allowing re-send).
     *
     * <p>Only the requester may cancel (T-09-02 — Elevation of Privilege mitigation).
     * The row is deleted (not set to CANCELLED) so that a new request can be sent later.
     *
     * @param requestId   UUID of the friend request row
     * @param currentUser authenticated user (from @AuthenticationPrincipal)
     * @throws ResponseStatusException 404 if not found; 403 if not the requester
     */
    @Transactional
    public void cancelRequest(UUID requestId, UserEntity currentUser) {
        FriendRequestEntity entity = findById(requestId);

        // T-09-02: Only requester may cancel
        if (!entity.getRequester().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to cancel this request");
        }

        friendRequestRepository.delete(entity);
    }

    /**
     * Get all PENDING friend requests received by the current user (for Feed discovery widget).
     *
     * <p>Results are ordered by createdAt DESC (newest first) so the most recent
     * pending requests appear at the top of the widget (D-09).
     *
     * @param currentUser authenticated user (from @AuthenticationPrincipal)
     * @return list of {@link FriendRequestDto} for PENDING received requests
     */
    @Transactional(readOnly = true)
    public List<FriendRequestDto> getPendingReceived(UserEntity currentUser) {
        return friendRequestRepository
                .findPendingReceivedByUserId(currentUser.getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Search for users by display name and annotate each result with the current friendship
     * state (DISC-01, D-05, D-06).
     *
     * <p><strong>No N+1 (RESEARCH Open Question 2):</strong>
     * After retrieving the result page, a single call to
     * {@link FriendRequestRepository#findRelationshipsForUser} fetches all relevant
     * friend_requests rows for the page's user ids. The status of each row is then
     * mapped in-memory to the appropriate {@link FriendStatus} value.
     *
     * <p><strong>T-09-04:</strong> The {@code query} parameter is passed as a JPQL bind
     * parameter — no string concatenation.
     *
     * @param query       search term (case-insensitive display name fragment)
     * @param pageable    page/size
     * @param currentUser authenticated user (results exclude this user, status computed relative to them)
     * @return paginated {@link UserSearchResultDto} with friendStatus per result
     */
    @Transactional(readOnly = true)
    public Page<UserSearchResultDto> searchUsers(String query, Pageable pageable, UserEntity currentUser) {
        Page<UserEntity> userPage = userRepository.searchByDisplayName(
                query, currentUser.getId(), pageable);

        // Single bulk query for all relevant friend_request rows — avoids N+1
        Set<UUID> pageUserIds = userPage.getContent().stream()
                .map(UserEntity::getId)
                .collect(Collectors.toSet());

        Map<UUID, FriendRequestEntity> relationshipMap = new HashMap<>();

        if (!pageUserIds.isEmpty()) {
            List<FriendRequestEntity> relationships =
                    friendRequestRepository.findRelationshipsForUser(currentUser.getId(), pageUserIds);

            for (FriendRequestEntity fr : relationships) {
                // For each row, map by the OTHER user's id
                UUID otherUserId = fr.getRequester().getId().equals(currentUser.getId())
                        ? fr.getRecipient().getId()
                        : fr.getRequester().getId();
                // Keep only the most relevant row per pair (overwrite with any ACCEPTED)
                FriendRequestEntity existing = relationshipMap.get(otherUserId);
                if (existing == null || "ACCEPTED".equals(fr.getStatus())) {
                    relationshipMap.put(otherUserId, fr);
                }
            }
        }

        return userPage.map(user -> {
            FriendRequestEntity fr = relationshipMap.get(user.getId());
            FriendStatus friendStatus;
            String requestId = null;

            if (fr == null || "REJECTED".equals(fr.getStatus()) || "CANCELLED".equals(fr.getStatus())) {
                friendStatus = FriendStatus.NONE;
            } else if ("ACCEPTED".equals(fr.getStatus())) {
                friendStatus = FriendStatus.ACCEPTED;
            } else if ("PENDING".equals(fr.getStatus())) {
                // PENDING: direction matters
                if (fr.getRequester().getId().equals(currentUser.getId())) {
                    friendStatus = FriendStatus.PENDING_SENT;
                } else {
                    friendStatus = FriendStatus.PENDING_RECEIVED;
                }
                requestId = fr.getId().toString();
            } else {
                friendStatus = FriendStatus.NONE;
            }

            return new UserSearchResultDto(
                    user.getId().toString(),
                    user.getDisplayName(),
                    friendStatus,
                    requestId
            );
        });
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    /**
     * Look up a {@link FriendRequestEntity} by id or throw 404.
     */
    private FriendRequestEntity findById(UUID requestId) {
        return friendRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Friend request not found"));
    }

    /**
     * Map a {@link FriendRequestEntity} to a {@link FriendRequestDto}.
     *
     * <p>The requester entity must be loaded — either by JOIN FETCH or by navigating
     * the LAZY proxy within an active transaction.
     */
    FriendRequestDto toDto(FriendRequestEntity entity) {
        return new FriendRequestDto(
                entity.getId().toString(),
                entity.getRequester().getId().toString(),
                entity.getRecipient().getId().toString(),
                entity.getRequester().getDisplayName(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
