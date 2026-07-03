package com.booktracker.social;

import com.booktracker.user.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for review like/unlike endpoints (DISC-04).
 *
 * <p>No class-level {@code @RequestMapping} — individual methods carry the full paths.
 * This follows the project convention established in {@link SocialController}.
 *
 * <p><strong>Security (threat model):</strong>
 * <ul>
 *   <li>T-09-07: Liker identity is ALWAYS from {@code @AuthenticationPrincipal UserEntity} —
 *       never from a path variable or request body. The path only identifies the entry to like.</li>
 *   <li>T-09-08: Unlike is scoped to the caller's own like (findByUserIdAndEntryId in service).</li>
 * </ul>
 *
 * <p><strong>Auth coverage:</strong>
 * Covered by the existing {@code anyRequest().authenticated()} rule in SecurityConfig.
 * No changes to SecurityConfig are required.
 *
 * <p><strong>@PathVariable UUID id:</strong>
 * Spring MVC automatically rejects malformed UUIDs with 400 Bad Request via type binding.
 */
@RestController
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    /**
     * POST /api/entries/{entryId}/like — like a shelf entry as the current user.
     *
     * <p>Returns 201 Created on success.
     * Returns 404 if the entry does not exist (T-09-09).
     * Returns 409 if the current user has already liked this entry (pair_uq).
     * Returns 401 if no JWT (SecurityConfig AuthenticationEntryPoint).
     *
     * <p>The liker is always {@code currentUser} from {@code @AuthenticationPrincipal} (T-09-07).
     *
     * @param entryId     UUID of the user_books entry to like (from path)
     * @param currentUser the authenticated user (from JWT principal — T-09-07)
     * @return 201 Created with empty body
     */
    @PostMapping("/api/entries/{entryId}/like")
    public ResponseEntity<Void> like(
            @PathVariable UUID entryId,
            @AuthenticationPrincipal UserEntity currentUser) {
        likeService.likeReview(entryId, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * DELETE /api/entries/{entryId}/like — remove the current user's like from an entry.
     *
     * <p>Returns 204 No Content on success.
     * Returns 404 if the current user has not liked this entry (T-09-08).
     * Returns 401 if no JWT (SecurityConfig AuthenticationEntryPoint).
     *
     * <p>Only the current user's own like row is removed (T-09-08 IDOR mitigation).
     *
     * @param entryId     UUID of the user_books entry to unlike (from path)
     * @param currentUser the authenticated user (from JWT principal — T-09-08)
     */
    @DeleteMapping("/api/entries/{entryId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlike(
            @PathVariable UUID entryId,
            @AuthenticationPrincipal UserEntity currentUser) {
        likeService.unlikeReview(entryId, currentUser);
    }
}
