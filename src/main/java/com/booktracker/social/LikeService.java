package com.booktracker.social;

import com.booktracker.notification.NotificationService;
import com.booktracker.notification.NotificationType;
import com.booktracker.shelf.ShelfRepository;
import com.booktracker.shelf.UserBookEntity;
import com.booktracker.user.UserEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Business logic for review likes (DISC-04).
 *
 * <p><strong>Security invariants (threat model):</strong>
 * <ul>
 *   <li>T-09-07: The liker is always {@code currentUser} from JWT — never from path or body.</li>
 *   <li>T-09-08: Unlike scoped to {@code findByUserIdAndEntryId(currentUser.getId(), entryId)}
 *       — only the caller's own like row is deleted. IDOR prevention.</li>
 *   <li>T-09-09: Entry existence is verified via ShelfRepository before saving — 404 if absent.
 *       The FK constraint is defense-in-depth.</li>
 * </ul>
 *
 * <p><strong>Duplicate like handling:</strong>
 * {@link DataIntegrityViolationException} from the {@code review_likes_pair_uq} constraint
 * is caught in {@link #likeReview} and thrown as 409 Conflict.
 * Uses {@code saveAndFlush()} to force the constraint check inside the try block
 * (same pattern as {@link com.booktracker.social.SocialService#follow}).
 *
 * <p><strong>Immutable likes:</strong>
 * Likes have no update path — only create and delete. No @PreUpdate in {@link LikeEntity}.
 *
 * <p><strong>Transactional import:</strong>
 * Uses {@code org.springframework.transaction.annotation.Transactional} (Spring),
 * NOT {@code jakarta.transaction.Transactional} — consistent with the project pattern.
 */
@Service
public class LikeService {

    private final LikeRepository likeRepository;
    private final ShelfRepository shelfRepository;
    private final NotificationService notificationService;

    /**
     * Constructor injection — NotificationService added in Plan 09-04 for REVIEW_LIKED trigger.
     *
     * @param likeRepository      like persistence store
     * @param shelfRepository     shelf entry lookup for entry existence check and owner resolution
     * @param notificationService notification persist+push for REVIEW_LIKED trigger
     */
    public LikeService(LikeRepository likeRepository, ShelfRepository shelfRepository,
                       NotificationService notificationService) {
        this.likeRepository = likeRepository;
        this.shelfRepository = shelfRepository;
        this.notificationService = notificationService;
    }

    /**
     * Like a shelf entry (user_books row) as the current user.
     *
     * <p>Steps:
     * <ol>
     *   <li>Verify the entry exists — 404 if not (T-09-09)</li>
     *   <li>Persist LikeEntity via {@code saveAndFlush()} — forces constraint check inside try</li>
     *   <li>Catch {@link DataIntegrityViolationException} → 409 Conflict (review_likes_pair_uq)</li>
     * </ol>
     *
     * @param entryId     the UUID of the user_books entry to like (from @PathVariable)
     * @param currentUser the authenticated liker (from @AuthenticationPrincipal — T-09-07)
     * @throws ResponseStatusException 404 if the entry does not exist; 409 if already liked
     */
    @Transactional
    public void likeReview(UUID entryId, UserEntity currentUser) {
        // T-09-09: Verify entry exists before writing — 404 if absent
        UserBookEntity entry = shelfRepository.findById(entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entry not found"));

        LikeEntity like = new LikeEntity();
        like.setUser(currentUser);
        like.setEntry(entry);

        try {
            // saveAndFlush forces the SQL INSERT within the transaction so
            // DataIntegrityViolationException fires here (not at commit).
            likeRepository.saveAndFlush(like);
        } catch (DataIntegrityViolationException e) {
            // review_likes_pair_uq constraint fired — user already liked this entry
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already liked this entry");
        }

        // NOTIF-01 trigger: persist+push REVIEW_LIKED notification to the entry owner,
        // but ONLY when the liker is NOT the entry owner (no self-like notifications).
        // Safe no-op when owner is not connected (RESEARCH Assumption A3).
        UserEntity owner = entry.getUser();
        if (!owner.getId().equals(currentUser.getId())) {
            notificationService.createNotification(owner, NotificationType.REVIEW_LIKED,
                    currentUser, entryId);
        }
    }

    /**
     * Unlike a shelf entry — remove the current user's own like.
     *
     * <p>Looks up the like scoped to {@code currentUser.getId()} as liker (T-09-08 IDOR mitigation).
     * Returns 404 if the current user has not liked this entry.
     *
     * @param entryId     the UUID of the user_books entry to unlike (from @PathVariable)
     * @param currentUser the authenticated user (from @AuthenticationPrincipal — T-09-08)
     * @throws ResponseStatusException 404 if the current user has not liked this entry
     */
    @Transactional
    public void unlikeReview(UUID entryId, UserEntity currentUser) {
        // T-09-08: Scope to the caller's own like row — never another user's like
        LikeEntity like = likeRepository
                .findByUserIdAndEntryId(currentUser.getId(), entryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Like not found"));
        likeRepository.delete(like);
    }
}
