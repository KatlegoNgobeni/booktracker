package com.booktracker.social;

import com.booktracker.goal.GoalEntity;
import com.booktracker.goal.GoalRepository;
import com.booktracker.shelf.ShelfRepository;
import com.booktracker.shelf.ShelfStatus;
import com.booktracker.shelf.UserBookEntity;
import com.booktracker.user.UserEntity;
import com.booktracker.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Business logic for the social layer: follow, unfollow, public profile, and activity feed.
 *
 * <p><strong>Security invariants (threat model):</strong>
 * <ul>
 *   <li>T-08-01: {@code follower} is always {@code currentUser} from JWT — never from request body.</li>
 *   <li>T-08-02: Feed query is scoped to {@code currentUser.getId()} — no userId from HTTP.</li>
 *   <li>T-08-03: Profile returns only {@code shelfStatus = READ} — WANT_TO_READ/CURRENTLY_READING private.</li>
 *   <li>T-08-04: Unfollow uses {@code findByFollowerIdAndFolloweeId(currentUser.getId(), followeeId)}
 *       — only the caller's own follow row is deleted.</li>
 *   <li>T-08-05: Self-follow is caught at service layer before any DB write — 400 BAD_REQUEST.</li>
 * </ul>
 *
 * <p><strong>Transactional import:</strong> Uses {@code org.springframework.transaction.annotation.Transactional}
 * (Spring), NOT {@code jakarta.transaction.Transactional} — avoids Pitfall 6 (CLAUDE.md §Version Gotchas).
 *
 * <p><strong>Constructor injection</strong> — no {@code @Autowired} field injection (CLAUDE.md).
 *
 * <p><strong>Duplicate follow handling (RESEARCH Pitfall 1):</strong>
 * {@code DataIntegrityViolationException} from the {@code follows_pair_uq} constraint is caught
 * in {@link #follow} and thrown as 409 with "Already following this user" — MUST NOT reach
 * {@code GlobalExceptionHandler.handleDuplicate} which returns "Email already registered".
 * Uses {@code saveAndFlush()} to force the constraint check inside the try block (same pattern
 * as {@code ShelfService.addToShelf}).
 *
 * <p><strong>Goal progress (RESEARCH Assumption A4):</strong>
 * {@link #computeGoalProgress} is duplicated from {@code StatsService} rather than extracted
 * to a shared utility — accepted for portfolio simplicity.
 */
@Service
public class SocialService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ShelfRepository shelfRepository;
    private final GoalRepository goalRepository;

    public SocialService(FollowRepository followRepository,
                         UserRepository userRepository,
                         ShelfRepository shelfRepository,
                         GoalRepository goalRepository) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.shelfRepository = shelfRepository;
        this.goalRepository = goalRepository;
    }

    /**
     * Follow another user.
     *
     * <p>Steps:
     * <ol>
     *   <li>Reject self-follow — 400 (T-08-05, Pitfall 6 — service check before DB constraint)</li>
     *   <li>Verify followee exists — 404 if not (T-08-06)</li>
     *   <li>Persist FollowEntity via {@code saveAndFlush()} — forces constraint check inside try</li>
     *   <li>Catch {@link DataIntegrityViolationException} → 409 "Already following this user" (Pitfall 1)</li>
     * </ol>
     *
     * @param followeeId  the UUID of the user to follow (from @PathVariable)
     * @param currentUser the authenticated user (from @AuthenticationPrincipal — T-08-01)
     * @return {@link FollowStatusDto}({@code following=true})
     * @throws ResponseStatusException 400 if self-follow; 404 if followee not found; 409 if already following
     */
    @Transactional
    public FollowStatusDto follow(UUID followeeId, UserEntity currentUser) {
        // T-08-05: Self-follow check at service layer — friendlier message than DB constraint (Pitfall 6)
        if (followeeId.equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot follow yourself");
        }

        // T-08-06: Verify followee exists
        UserEntity followee = userRepository.findById(followeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        FollowEntity follow = new FollowEntity();
        follow.setFollower(currentUser);
        follow.setFollowee(followee);

        try {
            // saveAndFlush forces the SQL INSERT within the transaction so
            // DataIntegrityViolationException fires here (not at commit),
            // allowing us to catch it and return 409 before GlobalExceptionHandler
            // gets a chance to return "Email already registered" (Pitfall 1).
            followRepository.saveAndFlush(follow);
        } catch (DataIntegrityViolationException e) {
            // follows_pair_uq constraint fired — already following this user.
            // Must NOT reach GlobalExceptionHandler.handleDuplicate (returns "Email already registered").
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already following this user");
        }

        return new FollowStatusDto(true);
    }

    /**
     * Unfollow a user.
     *
     * <p>Looks up the follow relationship scoped to {@code currentUser.getId()} as follower (T-08-04).
     * Returns 404 if the relationship does not exist.
     *
     * @param followeeId  the UUID of the user to unfollow (from @PathVariable)
     * @param currentUser the authenticated user (from @AuthenticationPrincipal — T-08-01)
     * @throws ResponseStatusException 404 if not currently following this user
     */
    @Transactional
    public void unfollow(UUID followeeId, UserEntity currentUser) {
        FollowEntity follow = followRepository
                .findByFollowerIdAndFolloweeId(currentUser.getId(), followeeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Not following this user"));
        followRepository.delete(follow);
    }

    /**
     * Assemble the public profile for a target user.
     *
     * <p>Only READ shelf entries are returned (T-08-03 — WANT_TO_READ and CURRENTLY_READING
     * are private). Reuses existing {@link ShelfRepository} and {@link GoalRepository}
     * queries — no new aggregation logic (RESEARCH Pattern 4).
     *
     * <p>8 queries total:
     * <ol>
     *   <li>userRepository.findById</li>
     *   <li>shelfRepository.findByUserAndShelfStatus (READ, paginated)</li>
     *   <li>count query (Pageable)</li>
     *   <li>goalRepository.findByUserIdAndYear</li>
     *   <li>shelfRepository.countBooksReadThisYear</li>
     *   <li>followRepository.countByFolloweeId</li>
     *   <li>followRepository.countByFollowerId</li>
     *   <li>followRepository.existsByFollowerIdAndFolloweeId</li>
     * </ol>
     *
     * @param targetId    UUID of the user whose profile is being viewed
     * @param currentUser the authenticated viewer (from @AuthenticationPrincipal)
     * @param pageable    page/size for the readEntries list
     * @return assembled {@link PublicProfileDto}
     * @throws ResponseStatusException 404 if the target user does not exist
     */
    @Transactional(readOnly = true)
    public PublicProfileDto getPublicProfile(UUID targetId, UserEntity currentUser, Pageable pageable) {
        // T-08-06: Consistent 404 for unknown user
        UserEntity target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        int currentYear = LocalDate.now().getYear();

        // T-08-03: Only READ entries — WANT_TO_READ and CURRENTLY_READING are private
        Page<UserBookEntity> readEntries =
                shelfRepository.findByUserAndShelfStatus(target, ShelfStatus.READ, pageable);

        // Goal progress (reuses GoalRepository pattern from StatsService)
        Integer goalTarget = goalRepository.findByUserIdAndYear(targetId, currentYear)
                .map(GoalEntity::getTargetCount)
                .orElse(null);

        long booksReadThisYear = shelfRepository.countBooksReadThisYear(targetId, currentYear);

        // Follow counts for the target
        long followerCount = followRepository.countByFolloweeId(targetId);
        long followingCount = followRepository.countByFollowerId(targetId);

        // isFollowing: does the current viewer follow the target?
        boolean isFollowing = followRepository.existsByFollowerIdAndFolloweeId(
                currentUser.getId(), targetId);

        Double goalProgressPercent = computeGoalProgress(booksReadThisYear, goalTarget);

        Page<PublicShelfEntryDto> readEntryDtos = readEntries.map(this::toPublicEntryDto);

        return new PublicProfileDto(
                target.getId().toString(),
                target.getDisplayName(),
                followerCount,
                followingCount,
                isFollowing,
                goalTarget,
                goalProgressPercent,
                booksReadThisYear,
                readEntryDtos
        );
    }

    /**
     * Get the activity feed for the authenticated user — a paginated list of READ shelf
     * entries from users the current user follows, ordered by dateFinished DESC.
     *
     * <p>Feed is scoped to {@code currentUser.getId()} from JWT — no userId from HTTP (T-08-02).
     * The V2 composite index on {@code user_books(user_id, shelf_status, date_finished DESC)}
     * ensures efficient ordering.
     *
     * @param pageable    page/size for the feed
     * @param currentUser the authenticated user (from @AuthenticationPrincipal — T-08-02)
     * @return paginated {@link FeedItemDto} list
     */
    @Transactional(readOnly = true)
    public Page<FeedItemDto> getFeed(Pageable pageable, UserEntity currentUser) {
        // T-08-02: Feed scoped to currentUser.getId() — never a userId from HTTP
        Page<UserBookEntity> page = followRepository.findFeedForUser(currentUser.getId(), pageable);
        return page.map(this::toFeedItemDto);
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    /**
     * Compute goalProgressPercent with the business rules from {@code StatsService}
     * (RESEARCH Assumption A4 — duplication accepted for portfolio simplicity):
     * <ul>
     *   <li>Returns {@code null} when {@code goalTarget} is null (no goal set)</li>
     *   <li>Returns {@code 100.0} when {@code goalTarget == 0} (divide-by-zero guard)</li>
     *   <li>Returns {@code Math.min(raw, 100.0)} otherwise — capped at 100</li>
     * </ul>
     */
    private Double computeGoalProgress(long booksReadThisYear, Integer goalTarget) {
        if (goalTarget == null) {
            return null;
        }
        if (goalTarget == 0) {
            return 100.0;
        }
        double raw = (booksReadThisYear * 100.0) / goalTarget;
        return Math.min(raw, 100.0);
    }

    /**
     * Map a {@link UserBookEntity} to a {@link PublicShelfEntryDto}.
     *
     * <p>The short olKey strips the {@code /works/} prefix to match the convention in
     * {@code ShelfService.toDto()} (D-08).
     * Rating is null-safe Short → Integer conversion.
     */
    private PublicShelfEntryDto toPublicEntryDto(UserBookEntity ub) {
        var book = ub.getBook();
        return new PublicShelfEntryDto(
                ub.getId().toString(),
                book.getTitle(),
                book.getAuthors(),
                book.getCoverId(),
                book.getOpenLibraryKey().replaceFirst("^/works/", ""),
                ub.getRating() != null ? ub.getRating().intValue() : null,
                ub.getReview(),
                ub.getDateFinished()
        );
    }

    /**
     * Map a {@link UserBookEntity} to a {@link FeedItemDto}.
     *
     * <p>Both {@code ub.book} and {@code ub.user} must be loaded (not lazy proxies) —
     * ensured by the JOIN FETCH in {@link FollowRepository#findFeedForUser}.
     * The short olKey strips {@code /works/} prefix per project convention.
     */
    private FeedItemDto toFeedItemDto(UserBookEntity ub) {
        var book = ub.getBook();
        var user = ub.getUser();
        return new FeedItemDto(
                ub.getId().toString(),
                user.getId().toString(),
                user.getDisplayName(),
                book.getTitle(),
                book.getOpenLibraryKey().replaceFirst("^/works/", ""),
                book.getCoverId(),
                book.getAuthors(),
                ub.getRating() != null ? ub.getRating().intValue() : null,
                ub.getReview(),
                ub.getDateFinished(),
                ub.getCreatedAt()
        );
    }
}
