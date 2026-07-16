package com.booktracker.social;

import com.booktracker.user.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for social features — follow/unfollow, public profile, and activity feed.
 *
 * <p>All four endpoints are under {@code /api} (no {@code @RequestMapping} class-level prefix —
 * individual {@code @*Mapping} annotations carry the full paths). This follows the 07-01 convention
 * where full paths are declared per-method.
 *
 * <p><strong>Security (RESEARCH Pattern 5):</strong>
 * All endpoints require a valid JWT (covered by {@code anyRequest().authenticated()} in
 * {@code SecurityConfig} — no changes to the security config for Phase 8).
 * The follower/viewer identity is ALWAYS {@code @AuthenticationPrincipal UserEntity currentUser}
 * — the authenticated user's identity is ALWAYS from the JWT principal (T-08-01).
 *
 * <p><strong>@PathVariable UUID id:</strong>
 * Spring MVC automatically rejects malformed UUIDs with 400 Bad Request via type binding.
 * No explicit UUID format validation is needed (same pattern as {@code ShelfController}).
 *
 * <p><strong>No SecurityConfig changes:</strong>
 * The existing {@code /api/**} authenticated rule in {@code SecurityConfig} covers all
 * four new endpoints. No changes to SecurityConfig are required or made.
 */
@RestController
public class SocialController {

    private final SocialService socialService;

    public SocialController(SocialService socialService) {
        this.socialService = socialService;
    }

    /**
     * POST /api/users/{id}/follow — follow the specified user.
     *
     * <p>Returns 201 Created + {@code {"following": true}} on success.
     * Returns 400 if attempting to follow yourself (T-08-05).
     * Returns 404 if the target user does not exist (T-08-06).
     * Returns 409 if already following this user (follows_pair_uq constraint).
     * Returns 401 if no JWT (existing SecurityConfig AuthenticationEntryPoint).
     *
     * <p>The follower is always {@code currentUser} from {@code @AuthenticationPrincipal} —
     * NEVER from the request body (T-08-01 IDOR mitigation, RESEARCH Anti-Pattern).
     *
     * @param id          UUID of the user to follow (from path; malformed → 400 automatic)
     * @param currentUser the authenticated follower (from JWT principal)
     * @return 201 Created + FollowStatusDto{following=true}
     */
    @PostMapping("/api/users/{id}/follow")
    public ResponseEntity<FollowStatusDto> follow(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserEntity currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(socialService.follow(id, currentUser));
    }

    /**
     * DELETE /api/users/{id}/follow — unfollow the specified user.
     *
     * <p>Returns 204 No Content on success.
     * Returns 404 if not currently following this user.
     * Returns 401 if no JWT (existing SecurityConfig).
     *
     * <p>The follow row is looked up by {@code (currentUser.getId(), followeeId)} — only
     * the caller's own follow relationship can be deleted (T-08-04 Tampering mitigation).
     *
     * @param id          UUID of the user to unfollow (from path; malformed → 400 automatic)
     * @param currentUser the authenticated user (from JWT principal)
     */
    @DeleteMapping("/api/users/{id}/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserEntity currentUser) {
        socialService.unfollow(id, currentUser);
    }

    /**
     * GET /api/users/{id}/profile — get the public profile of the specified user.
     *
     * <p>Returns 200 OK + {@link PublicProfileDto} containing displayName, follower/following
     * counts, isFollowing, goal progress, booksReadThisYear, and a paginated list of only
     * READ shelf entries (T-08-03 — WANT_TO_READ/CURRENTLY_READING are private).
     * Returns 404 if the target user does not exist.
     *
     * <p>The current user's identity is from {@code @AuthenticationPrincipal} — used to
     * determine {@code isFollowing} (T-08-02).
     *
     * @param id          UUID of the user whose profile to view
     * @param pageable    page/size for the readEntries list (default page=0, size=20)
     * @param currentUser the authenticated viewer (from JWT principal)
     * @return PublicProfileDto with paginated READ entries
     */
    @GetMapping("/api/users/{id}/profile")
    public PublicProfileDto getPublicProfile(
            @PathVariable UUID id,
            Pageable pageable,
            @AuthenticationPrincipal UserEntity currentUser) {
        return socialService.getPublicProfile(id, currentUser, pageable);
    }

    /**
     * GET /api/feed — get the activity feed for the authenticated user.
     *
     * <p>Returns 200 OK + paginated {@link FeedItemDto} list containing READ entries from
     * users the current user follows, ordered by dateFinished DESC.
     * Returns an empty page when the current user follows nobody.
     *
     * <p>Feed is always scoped to the authenticated user (T-08-02 — no userId from HTTP).
     *
     * @param pageable    page/size for the feed (default page=0, size=20)
     * @param currentUser the authenticated user (from JWT principal — T-08-02)
     * @return paginated FeedItemDto list ordered by dateFinished DESC
     */
    @GetMapping("/api/feed")
    public Page<FeedItemDto> getFeed(
            Pageable pageable,
            @AuthenticationPrincipal UserEntity currentUser) {
        return socialService.getFeed(pageable, currentUser);
    }

    /**
     * GET /api/feed/friends-reading — get what accepted friends are currently reading.
     *
     * <p>Returns 200 OK + paginated {@link FriendsReadingItemDto} list containing
     * CURRENTLY_READING entries from accepted friends in either direction, ordered by
     * createdAt DESC. Returns an empty page when the current user has no accepted friends
     * with active reads.
     *
     * <p>Feed is always scoped to the authenticated user (T-15-01 — no userId from HTTP).
     * Content is restricted to bidirectional ACCEPTED friends (T-15-02).
     *
     * @param pageable    page/size for the feed (default page=0, size=20)
     * @param currentUser the authenticated user (from JWT principal — T-15-01)
     * @return paginated FriendsReadingItemDto list ordered by createdAt DESC
     */
    @GetMapping("/api/feed/friends-reading")
    public Page<FriendsReadingItemDto> getFriendsReading(
            Pageable pageable,
            @AuthenticationPrincipal UserEntity currentUser) {
        return socialService.getFriendsCurrentlyReading(pageable, currentUser);
    }
}
