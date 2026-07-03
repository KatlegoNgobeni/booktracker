package com.booktracker.social;

import com.booktracker.shelf.UserBookEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link FriendRequestEntity}.
 *
 * <p>All queries use explicit {@code @Query} annotations rather than derived method names
 * (same pattern as {@link FollowRepository}).
 *
 * <p><strong>JOIN FETCH + explicit countQuery (RESEARCH Anti-Pattern / Pitfall, Pattern 5):</strong>
 * {@link #findFeedForFriends} uses JOIN FETCH and requires an explicit {@code countQuery} —
 * Hibernate 6 cannot auto-derive a count query from JPQL with JOIN FETCH (same pitfall as
 * the follow-feed query in FollowRepository).
 *
 * <p><strong>Bidirectional friend queries (RESEARCH Pitfall 3):</strong>
 * JPQL does not support UNION. Two independent IN subqueries joined with OR implement the
 * bidirectional "current user is requester OR current user is recipient" semantics.
 *
 * <p><strong>Social cohesion (RESEARCH Anti-Pattern):</strong>
 * Feed and friend-status queries live here (not in FollowRepository or ShelfRepository)
 * because they are inherently about the friend_requests social graph.
 */
public interface FriendRequestRepository extends JpaRepository<FriendRequestEntity, UUID> {

    /**
     * Check if any active (PENDING or ACCEPTED) relationship exists between two users
     * in either direction.
     *
     * <p>Used by {@link FriendRequestService#sendRequest} to prevent dual-pending states
     * (RESEARCH Pitfall 5). The V3 {@code friend_requests_pair_uq} unique constraint only
     * covers the same ordered pair — this query covers both orderings.
     *
     * @param uid1 UUID of the first user
     * @param uid2 UUID of the second user
     * @return true if any PENDING or ACCEPTED relationship exists in either direction
     */
    @Query("SELECT COUNT(fr) > 0 FROM FriendRequestEntity fr " +
           "WHERE (fr.requester.id = :uid1 AND fr.recipient.id = :uid2 " +
           "    OR fr.requester.id = :uid2 AND fr.recipient.id = :uid1) " +
           "AND fr.status IN ('PENDING', 'ACCEPTED')")
    boolean existsActiveRelationship(@Param("uid1") UUID uid1, @Param("uid2") UUID uid2);

    /**
     * Find all PENDING friend requests received by the given user, newest first.
     *
     * <p>JOIN FETCHes the requester so {@link FriendRequestDto#requesterDisplayName()} can be
     * assembled without a secondary query.
     *
     * <p>Used by {@code GET /api/friend-requests/pending-received} (Feed discovery widget,
     * DISC-03, D-09).
     *
     * @param userId UUID of the recipient
     * @return list of PENDING requests addressed to this user, ordered by createdAt DESC
     */
    @Query("SELECT fr FROM FriendRequestEntity fr " +
           "JOIN FETCH fr.requester " +
           "WHERE fr.recipient.id = :userId AND fr.status = 'PENDING' " +
           "ORDER BY fr.createdAt DESC")
    List<FriendRequestEntity> findPendingReceivedByUserId(@Param("userId") UUID userId);

    /**
     * Find all friend request rows where the current user is requester or recipient,
     * and the other party is in the given id set.
     *
     * <p>Used by the user search endpoint to compute {@code friendStatus} for a page of results
     * in a single bulk query — avoids N+1 (RESEARCH Open Question 2).
     *
     * @param currentUserId UUID of the authenticated user
     * @param otherIds      set of other user IDs appearing in the search result page
     * @return all relevant friend request rows (any status)
     */
    @Query("SELECT fr FROM FriendRequestEntity fr " +
           "WHERE (fr.requester.id = :currentUserId AND fr.recipient.id IN :otherIds) " +
           "   OR (fr.recipient.id = :currentUserId AND fr.requester.id IN :otherIds)")
    List<FriendRequestEntity> findRelationshipsForUser(
            @Param("currentUserId") UUID currentUserId,
            @Param("otherIds") Set<UUID> otherIds);

    /**
     * Activity feed based on accepted friendships — paginated list of READ user_books from
     * accepted friends in either direction.
     *
     * <p><strong>Bidirectional (D-03, Pattern 5):</strong>
     * Friends are users where the current user is either:
     * <ul>
     *   <li>the requester of an ACCEPTED request (friends are the recipients), or</li>
     *   <li>the recipient of an ACCEPTED request (friends are the requesters)</li>
     * </ul>
     *
     * <p><strong>countQuery is mandatory (RESEARCH Anti-Pattern / Pitfall):</strong>
     * Hibernate 6 cannot auto-derive a count query from JPQL with JOIN FETCH. The explicit
     * {@code countQuery} omits JOIN FETCH (not needed for counting).
     *
     * <p><strong>Scoped by JWT (T-08-02):</strong> {@code :userId} is always
     * {@code currentUser.getId()} from {@code @AuthenticationPrincipal} in
     * {@link SocialService#getFeed} — never from the HTTP request.
     *
     * @param userId   the authenticated user's UUID
     * @param pageable page/size/sort
     * @return paginated READ entries from accepted friends, ordered by dateFinished DESC
     */
    @Query(
        value =
            "SELECT ub FROM UserBookEntity ub " +
            "JOIN FETCH ub.book " +
            "JOIN FETCH ub.user " +
            "WHERE (ub.user.id IN (" +
            "    SELECT fr.recipient.id FROM FriendRequestEntity fr " +
            "    WHERE fr.requester.id = :userId AND fr.status = 'ACCEPTED'" +
            ") OR ub.user.id IN (" +
            "    SELECT fr.requester.id FROM FriendRequestEntity fr " +
            "    WHERE fr.recipient.id = :userId AND fr.status = 'ACCEPTED'" +
            ")) " +
            "AND ub.shelfStatus = 'READ' " +
            "AND ub.dateFinished IS NOT NULL " +
            "ORDER BY ub.dateFinished DESC",
        countQuery =
            "SELECT COUNT(ub) FROM UserBookEntity ub " +
            "WHERE (ub.user.id IN (" +
            "    SELECT fr.recipient.id FROM FriendRequestEntity fr " +
            "    WHERE fr.requester.id = :userId AND fr.status = 'ACCEPTED'" +
            ") OR ub.user.id IN (" +
            "    SELECT fr.requester.id FROM FriendRequestEntity fr " +
            "    WHERE fr.recipient.id = :userId AND fr.status = 'ACCEPTED'" +
            ")) " +
            "AND ub.shelfStatus = 'READ' " +
            "AND ub.dateFinished IS NOT NULL"
    )
    Page<UserBookEntity> findFeedForFriends(@Param("userId") UUID userId, Pageable pageable);
}
