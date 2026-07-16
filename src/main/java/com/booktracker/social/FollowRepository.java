package com.booktracker.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link FollowEntity}.
 *
 * <p>All queries use explicit {@code @Query} annotations rather than Spring Data derived
 * query names (RESEARCH Pattern 6 / Pitfall 7). With {@code @ManyToOne UserEntity follower}
 * (not a plain UUID column), derived names like {@code findByFollowerIdAndFolloweeId} are
 * ambiguous — explicit JPQL is unambiguous.
 *
 * <p><strong>Feed query:</strong>
 * The activity feed is now served by
 * {@link FriendRequestRepository#findFeedForFriends} (accepted friends, bidirectional)
 * and {@link FriendRequestRepository#findFriendsCurrentlyReading} (DISC-01).
 */
public interface FollowRepository extends JpaRepository<FollowEntity, UUID> {

    /**
     * Find a specific follow relationship between two users.
     *
     * <p>Used by {@code SocialService.unfollow()} to verify the follow exists
     * (404 if not) before deleting, and is safe for IDOR because the follower
     * is always the authenticated user (T-08-04).
     *
     * @param followerId the follower's UUID
     * @param followeeId the followee's UUID
     * @return the follow entity if it exists
     */
    @Query("SELECT f FROM FollowEntity f WHERE f.follower.id = :followerId AND f.followee.id = :followeeId")
    Optional<FollowEntity> findByFollowerIdAndFolloweeId(
            @Param("followerId") UUID followerId,
            @Param("followeeId") UUID followeeId);

    /**
     * Check whether a specific follow relationship exists.
     *
     * <p>Used by {@code SocialService.getPublicProfile()} to determine the
     * {@code isFollowing} field without loading the entity (T-08-03).
     *
     * @param followerId the potential follower's UUID
     * @param followeeId the potential followee's UUID
     * @return true if the follow relationship exists
     */
    @Query("SELECT COUNT(f) > 0 FROM FollowEntity f WHERE f.follower.id = :followerId AND f.followee.id = :followeeId")
    boolean existsByFollowerIdAndFolloweeId(
            @Param("followerId") UUID followerId,
            @Param("followeeId") UUID followeeId);

    /**
     * Count how many users follow the given user (follower count).
     *
     * @param followeeId the user being followed
     * @return count of followers
     */
    @Query("SELECT COUNT(f) FROM FollowEntity f WHERE f.followee.id = :followeeId")
    long countByFolloweeId(@Param("followeeId") UUID followeeId);

    /**
     * Count how many users the given user follows (following count).
     *
     * @param followerId the user doing the following
     * @return count of users being followed
     */
    @Query("SELECT COUNT(f) FROM FollowEntity f WHERE f.follower.id = :followerId")
    long countByFollowerId(@Param("followerId") UUID followerId);

}
