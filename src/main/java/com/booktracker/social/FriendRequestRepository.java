package com.booktracker.social;

import com.booktracker.shelf.UserBookEntity;
import com.booktracker.user.UserEntity;
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
    /**
     * Find all ACCEPTED friend request rows where the given user is either requester or recipient.
     *
     * <p>Used by {@link com.booktracker.shelf.ShelfService#updateMetadata} to fan out
     * {@code FRIEND_FINISHED_BOOK} notifications when a book is marked READ. The service
     * derives the "other" user from each row in Java (requester when current user is recipient,
     * recipient when current user is requester).
     *
     * <p>JOIN FETCHes both requester and recipient so the {@link UserEntity} objects
     * are fully initialised for notification routing without additional queries.
     *
     * <p><strong>Note (Hibernate 6):</strong> JPQL CASE WHEN cannot return entity objects in
     * Hibernate 6 (ClassCastException: SingleTableEntityPersister cannot be cast to
     * BasicValuedMapping). Returning the FriendRequestEntity and extracting the friend in Java
     * is the correct approach.
     *
     * @param userId UUID of the user whose accepted friends are needed
     * @return list of ACCEPTED {@link FriendRequestEntity} rows involving the given user
     */
    @Query("SELECT fr FROM FriendRequestEntity fr " +
           "JOIN FETCH fr.requester " +
           "JOIN FETCH fr.recipient " +
           "WHERE (fr.requester.id = :userId OR fr.recipient.id = :userId) " +
           "  AND fr.status = 'ACCEPTED'")
    List<FriendRequestEntity> findAcceptedRelationships(@Param("userId") UUID userId);

    /**
     * Count accepted mutual friends for a user in either direction.
     *
     * @param userId UUID of the user
     * @return number of ACCEPTED friend_requests rows where the user is requester or recipient
     */
    @Query("SELECT COUNT(fr) FROM FriendRequestEntity fr " +
           "WHERE (fr.requester.id = :userId OR fr.recipient.id = :userId) " +
           "AND fr.status = 'ACCEPTED'")
    long countAcceptedFriends(@Param("userId") UUID userId);

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

    /**
     * Friends-reading feed — paginated list of CURRENTLY_READING user_books from accepted
     * friends in either direction, ordered by createdAt DESC.
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
     * {@code countQuery} omits JOIN FETCH (not needed for counting) and ORDER BY.
     *
     * <p><strong>Differences from findFeedForFriends:</strong>
     * <ul>
     *   <li>Filters {@code ub.shelfStatus = 'CURRENTLY_READING'} (not {@code 'READ'})</li>
     *   <li>No {@code ub.dateFinished IS NOT NULL} clause (in-progress books have no finish date)</li>
     *   <li>Ordered by {@code ub.createdAt DESC} (not {@code ub.dateFinished DESC})</li>
     * </ul>
     *
     * <p><strong>Scoped by JWT (T-15-01):</strong> {@code :userId} is always
     * {@code currentUser.getId()} from {@code @AuthenticationPrincipal} in
     * {@link SocialService#getFriendsCurrentlyReading} — never from the HTTP request.
     *
     * <p><strong>Information Disclosure (T-15-02):</strong>
     * JPQL WHERE clause restricts to bidirectional ACCEPTED friends only — same guard as
     * {@link #findFeedForFriends}. Non-friends' CURRENTLY_READING entries never appear.
     *
     * @param userId   the authenticated user's UUID
     * @param pageable page/size/sort
     * @return paginated CURRENTLY_READING entries from accepted friends, ordered by createdAt DESC
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
            "AND ub.shelfStatus = 'CURRENTLY_READING' " +
            "ORDER BY ub.createdAt DESC",
        countQuery =
            "SELECT COUNT(ub) FROM UserBookEntity ub " +
            "WHERE (ub.user.id IN (" +
            "    SELECT fr.recipient.id FROM FriendRequestEntity fr " +
            "    WHERE fr.requester.id = :userId AND fr.status = 'ACCEPTED'" +
            ") OR ub.user.id IN (" +
            "    SELECT fr.requester.id FROM FriendRequestEntity fr " +
            "    WHERE fr.recipient.id = :userId AND fr.status = 'ACCEPTED'" +
            ")) " +
            "AND ub.shelfStatus = 'CURRENTLY_READING'"
    )
    Page<UserBookEntity> findFriendsCurrentlyReading(@Param("userId") UUID userId, Pageable pageable);
}
