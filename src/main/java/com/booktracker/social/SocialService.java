package com.booktracker.social;

import com.booktracker.goal.GoalEntity;
import com.booktracker.goal.GoalRepository;
import com.booktracker.shelf.ShelfRepository;
import com.booktracker.shelf.ShelfStatus;
import com.booktracker.shelf.UserBookEntity;
import com.booktracker.user.UserEntity;
import com.booktracker.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Business logic for the social layer: public profile and activity feed.
 *
 * <p><strong>Security invariants (threat model):</strong>
 * <ul>
 *   <li>T-08-02: Feed query is scoped to {@code currentUser.getId()} — no userId from HTTP.</li>
 *   <li>T-08-03: Profile returns only {@code shelfStatus = READ} — WANT_TO_READ/CURRENTLY_READING private.</li>
 * </ul>
 *
 * <p><strong>Transactional import:</strong> Uses {@code org.springframework.transaction.annotation.Transactional}
 * (Spring), NOT {@code jakarta.transaction.Transactional} — avoids Pitfall 6 (CLAUDE.md §Version Gotchas).
 *
 * <p><strong>Constructor injection</strong> — no {@code @Autowired} field injection (CLAUDE.md).
 *
 * <p><strong>Goal progress (RESEARCH Assumption A4):</strong>
 * {@link #computeGoalProgress} is duplicated from {@code StatsService} rather than extracted
 * to a shared utility — accepted for portfolio simplicity.
 */
@Service
public class SocialService {

    private final UserRepository userRepository;
    private final ShelfRepository shelfRepository;
    private final GoalRepository goalRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final LikeRepository likeRepository;

    public SocialService(UserRepository userRepository,
                         ShelfRepository shelfRepository,
                         GoalRepository goalRepository,
                         FriendRequestRepository friendRequestRepository,
                         LikeRepository likeRepository) {
        this.userRepository = userRepository;
        this.shelfRepository = shelfRepository;
        this.goalRepository = goalRepository;
        this.friendRequestRepository = friendRequestRepository;
        this.likeRepository = likeRepository;
    }

    /**
     * Assemble the public profile for a target user.
     *
     * <p>Only READ shelf entries are returned (T-08-03 — WANT_TO_READ and CURRENTLY_READING
     * are private). Reuses existing {@link ShelfRepository} and {@link GoalRepository}
     * queries — no new aggregation logic (RESEARCH Pattern 4).
     *
     * <p>6 queries total:
     * <ol>
     *   <li>userRepository.findById</li>
     *   <li>shelfRepository.findByUserAndShelfStatus (READ, paginated)</li>
     *   <li>count query (Pageable)</li>
     *   <li>goalRepository.findByUserIdAndYear</li>
     *   <li>shelfRepository.countBooksReadThisYear</li>
     *   <li>friendRequestRepository.countAcceptedFriends</li>
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

        // Friend count: number of accepted mutual friends for the target
        long friendCount = friendRequestRepository.countAcceptedFriends(targetId);

        Double goalProgressPercent = computeGoalProgress(booksReadThisYear, goalTarget);

        UUID currentUserId = currentUser.getId();
        Page<PublicShelfEntryDto> readEntryDtos = readEntries.map(ub -> toPublicEntryDto(ub, currentUserId));

        return new PublicProfileDto(
                target.getId().toString(),
                target.getDisplayName(),
                friendCount,
                goalTarget,
                goalProgressPercent,
                booksReadThisYear,
                readEntryDtos,
                target.getProfilePhotoUrl()
        );
    }

    /**
     * Get the activity feed for the authenticated user — a paginated list of READ shelf
     * entries from accepted friends in either direction, ordered by dateFinished DESC.
     *
     * <p>Feed is scoped to {@code currentUser.getId()} from JWT — no userId from HTTP (T-08-02).
     *
     * @param pageable    page/size for the feed
     * @param currentUser the authenticated user (from @AuthenticationPrincipal — T-08-02)
     * @return paginated {@link FeedItemDto} list from accepted friends
     */
    @Transactional(readOnly = true)
    public Page<FeedItemDto> getFeed(Pageable pageable, UserEntity currentUser) {
        // T-08-02: currentUser.getId() is from JWT — never from HTTP
        Page<UserBookEntity> page = friendRequestRepository.findFeedForFriends(
                currentUser.getId(), pageable);
        return page.map(this::toFeedItemDto);
    }

    /**
     * Get the friends-reading feed for the authenticated user — a paginated list of
     * CURRENTLY_READING shelf entries from accepted friends in either direction, ordered
     * by createdAt DESC.
     *
     * <p>Feed is scoped to {@code currentUser.getId()} from JWT — no userId from HTTP (T-15-01).
     *
     * @param pageable    page/size for the feed
     * @param currentUser the authenticated user (from @AuthenticationPrincipal — T-15-01)
     * @return paginated {@link FriendsReadingItemDto} list from accepted friends
     */
    @Transactional(readOnly = true)
    public Page<FriendsReadingItemDto> getFriendsCurrentlyReading(Pageable pageable, UserEntity currentUser) {
        // T-15-01: currentUser.getId() is from JWT — never from HTTP
        Page<UserBookEntity> page = friendRequestRepository.findFriendsCurrentlyReading(
                currentUser.getId(), pageable);
        return page.map(this::toFriendsReadingItemDto);
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
     *
     * <p>{@code likeCount} and {@code likedByMe} (DISC-04) are populated from
     * {@link LikeRepository} queries. {@code likedByMe} is scoped to {@code currentUserId}
     * from the viewing user's JWT — never from the request (T-09-07 IDOR mitigation).
     *
     * @param ub            the shelf entry to map
     * @param currentUserId the UUID of the authenticated viewer (from @AuthenticationPrincipal)
     */
    private PublicShelfEntryDto toPublicEntryDto(UserBookEntity ub, UUID currentUserId) {
        var book = ub.getBook();
        UUID entryId = ub.getId();
        int likeCount = (int) likeRepository.countByEntryId(entryId);
        boolean likedByMe = likeRepository.existsByUserIdAndEntryId(currentUserId, entryId);
        return new PublicShelfEntryDto(
                entryId.toString(),
                book.getTitle(),
                book.getAuthors(),
                book.getCoverId(),
                book.getOpenLibraryKey().replaceFirst("^/works/", ""),
                ub.getRating() != null ? ub.getRating().intValue() : null,
                ub.getReview(),
                ub.getDateFinished(),
                likeCount,
                likedByMe
        );
    }

    /**
     * Map a {@link UserBookEntity} to a {@link FriendsReadingItemDto}.
     *
     * <p>Both {@code ub.book} and {@code ub.user} must be loaded (not lazy proxies) —
     * ensured by the JOIN FETCH in {@link FriendRequestRepository#findFriendsCurrentlyReading}.
     * The short olKey strips {@code /works/} prefix per project convention.
     * Does NOT include rating, review, or dateFinished — not applicable for CURRENTLY_READING.
     */
    private FriendsReadingItemDto toFriendsReadingItemDto(UserBookEntity ub) {
        var book = ub.getBook();
        var user = ub.getUser();
        return new FriendsReadingItemDto(
                ub.getId().toString(),
                user.getId().toString(),
                user.getDisplayName(),
                book.getTitle(),
                book.getOpenLibraryKey().replaceFirst("^/works/", ""),
                book.getCoverId(),
                book.getAuthors(),
                user.getProfilePhotoUrl()
        );
    }

    /**
     * Map a {@link UserBookEntity} to a {@link FeedItemDto}.
     *
     * <p>Both {@code ub.book} and {@code ub.user} must be loaded (not lazy proxies) —
     * ensured by the JOIN FETCH in {@link FriendRequestRepository#findFeedForFriends}.
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
                ub.getCreatedAt(),
                user.getProfilePhotoUrl()
        );
    }
}
