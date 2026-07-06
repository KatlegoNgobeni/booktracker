package com.booktracker.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the {@code review_likes} table.
 *
 * <p>All methods use explicit {@code @Query} JPQL to avoid relying on Spring Data
 * method-name derivation for aggregate queries, which is less expressive for counts
 * and existence checks (CLAUDE.md patterns).
 *
 * <p>Used by:
 * <ul>
 *   <li>{@link LikeService} — for likeReview / unlikeReview operations</li>
 *   <li>{@link SocialService} — for populating likeCount + likedByMe in PublicShelfEntryDto</li>
 * </ul>
 */
public interface LikeRepository extends JpaRepository<LikeEntity, UUID> {

    /**
     * Count how many users have liked the given entry.
     * Used to populate {@code likeCount} in {@link PublicShelfEntryDto}.
     *
     * @param entryId the UUID of the user_books entry
     * @return total number of likes on that entry
     */
    @Query("SELECT COUNT(l) FROM LikeEntity l WHERE l.entry.id = :entryId")
    long countByEntryId(@Param("entryId") UUID entryId);

    /**
     * Check whether a specific user has already liked a specific entry.
     * Used to populate {@code likedByMe} in {@link PublicShelfEntryDto}.
     *
     * <p>Uses {@code COUNT(l) > 0} pattern consistent with similar existence checks
     * in other repositories (FriendRequestRepository).
     *
     * @param userId  the UUID of the user
     * @param entryId the UUID of the user_books entry
     * @return true if the user has liked the entry, false otherwise
     */
    @Query("SELECT COUNT(l) > 0 FROM LikeEntity l WHERE l.user.id = :userId AND l.entry.id = :entryId")
    boolean existsByUserIdAndEntryId(@Param("userId") UUID userId, @Param("entryId") UUID entryId);

    /**
     * Find a specific like row by the user and entry combination.
     * Used by {@link LikeService#unlikeReview} to locate and delete the caller's own like.
     *
     * <p>T-09-08: The caller's own like is identified by {@code (currentUser.getId(), entryId)} —
     * never by a like ID from the request — preventing IDOR.
     *
     * @param userId  the UUID of the user (from @AuthenticationPrincipal, not request body)
     * @param entryId the UUID of the user_books entry
     * @return Optional containing the LikeEntity if found, empty if the user hasn't liked this entry
     */
    @Query("SELECT l FROM LikeEntity l WHERE l.user.id = :userId AND l.entry.id = :entryId")
    Optional<LikeEntity> findByUserIdAndEntryId(@Param("userId") UUID userId, @Param("entryId") UUID entryId);
}
