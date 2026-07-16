package com.booktracker.social;

import com.booktracker.user.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for social features — public profile and activity feed.
 *
 * <p>All endpoints are under {@code /api} (no {@code @RequestMapping} class-level prefix —
 * individual {@code @*Mapping} annotations carry the full paths).
 *
 * <p><strong>Security:</strong>
 * All endpoints require a valid JWT (covered by {@code anyRequest().authenticated()} in
 * {@code SecurityConfig}). The viewer identity is ALWAYS {@code @AuthenticationPrincipal UserEntity currentUser}
 * — the authenticated user's identity is ALWAYS from the JWT principal.
 *
 * <p><strong>@PathVariable UUID id:</strong>
 * Spring MVC automatically rejects malformed UUIDs with 400 Bad Request via type binding.
 */
@RestController
public class SocialController {

    private final SocialService socialService;

    public SocialController(SocialService socialService) {
        this.socialService = socialService;
    }

    /**
     * GET /api/users/{id}/profile — get the public profile of the specified user.
     *
     * <p>Returns 200 OK + {@link PublicProfileDto} containing displayName, friend count,
     * goal progress, booksReadThisYear, and a paginated list of only READ shelf entries
     * (T-08-03 — WANT_TO_READ/CURRENTLY_READING are private).
     * Returns 404 if the target user does not exist.
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
     * accepted friends, ordered by dateFinished DESC.
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
     * createdAt DESC.
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
