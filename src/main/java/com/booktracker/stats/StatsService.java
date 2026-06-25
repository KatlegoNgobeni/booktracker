package com.booktracker.stats;

import com.booktracker.goal.GoalEntity;
import com.booktracker.goal.GoalRepository;
import com.booktracker.shelf.ShelfRepository;
import com.booktracker.shelf.UserBookEntity;
import com.booktracker.user.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Assembles the computed analytics DTO for {@code GET /api/stats} (STATS-02, D-13).
 *
 * <p><strong>Responsibilities (D-13):</strong> The repository tier provides raw aggregation
 * query results; this service is responsible for all business-rule assembly:
 * <ul>
 *   <li>Capping {@code goalProgressPercent} at 100.0 (specifics, D-08)</li>
 *   <li>Guarding against division by zero when {@code goalTarget == 0} (RESEARCH Pitfall 3)</li>
 *   <li>Passing through {@code null} from {@link ShelfRepository#sumPagesReadThisYear} — no
 *       COALESCE treatment (RESEARCH Pitfall 4 / D-09)</li>
 *   <li>Building the 12-element {@code booksPerMonth} array by iterating READ entries and
 *       bucketing by {@code dateFinished.getMonthValue() - 1} (D-08, D-11)</li>
 *   <li>Computing longestBook / shortestBook via stream max/min on non-null page_count
 *       entries from all years (D-10)</li>
 * </ul>
 *
 * <p><strong>Transaction strategy:</strong> Annotated {@code @Transactional(readOnly = true)}
 * because the method loads entity lists via JOIN FETCH — the open session is required for
 * lazy association access. Read-only mode signals Hibernate to skip dirty-checking.
 *
 * <p><strong>Constructor injection</strong> — no {@code @Autowired} field injection (CLAUDE.md).
 *
 * <p><strong>No Flyway migration</strong> — all stats are computed on-the-fly (D-12).
 */
@Service
public class StatsService {

    private final ShelfRepository shelfRepository;
    private final GoalRepository goalRepository;

    public StatsService(ShelfRepository shelfRepository, GoalRepository goalRepository) {
        this.shelfRepository = shelfRepository;
        this.goalRepository = goalRepository;
    }

    /**
     * Compute and assemble the full analytics DTO for the authenticated user.
     *
     * <p>All aggregation queries are scoped to {@code user.getId()} — the userId is never
     * accepted from the HTTP request (T-05-02 IDOR mitigation, D-03, D-06).
     *
     * @param user the authenticated user (from {@code @AuthenticationPrincipal})
     * @return the assembled analytics DTO
     */
    @Transactional(readOnly = true)
    public StatsDto getStats(UserEntity user) {
        int currentYear = LocalDate.now().getYear();
        UUID userId = user.getId();

        // ---- Scalar aggregates ----
        long booksReadAllTime = shelfRepository.countBooksReadAllTime(userId);
        long booksReadThisYear = shelfRepository.countBooksReadThisYear(userId, currentYear);
        long currentlyReadingCount = shelfRepository.countCurrentlyReading(userId);

        Double averageRating = shelfRepository.avgRating(userId);
        Double averageBookLength = shelfRepository.avgBookLength(userId);

        // pagesReadThisYear: pass null through directly — no COALESCE (D-09, Pitfall 4).
        // The null signals "no qualifying entries this year" (all page_count null or no READ entries).
        Long pagesReadThisYear = shelfRepository.sumPagesReadThisYear(userId, currentYear);

        // ---- Goal fields ----
        Optional<GoalEntity> goalOpt = goalRepository.findByUserIdAndYear(userId, currentYear);
        Integer goalTarget = goalOpt.map(GoalEntity::getTargetCount).orElse(null);
        Double goalProgressPercent = computeGoalProgress(booksReadThisYear, goalTarget);

        // ---- booksPerMonth: 12-element array, index 0 = January (D-08, D-11) ----
        int[] booksPerMonth = new int[12]; // all zeros by default
        List<UserBookEntity> readThisYear = shelfRepository.findReadEntriesThisYear(userId, currentYear);
        for (UserBookEntity ub : readThisYear) {
            if (ub.getDateFinished() != null) {
                // LocalDate.getMonthValue() returns 1-based (1=Jan..12=Dec) → subtract 1 for 0-indexed array
                booksPerMonth[ub.getDateFinished().getMonthValue() - 1]++;
            }
        }

        // ---- longestBook / shortestBook (D-10): all-time READ entries with non-null page_count ----
        List<UserBookEntity> readWithPages = shelfRepository.findReadEntriesWithPageCount(userId);

        BookSummaryDto longestBook = readWithPages.stream()
                .max(Comparator.comparingInt(ub -> ub.getBook().getPageCount()))
                .map(ub -> new BookSummaryDto(ub.getBook().getTitle(), ub.getBook().getPageCount()))
                .orElse(null);

        BookSummaryDto shortestBook = readWithPages.stream()
                .min(Comparator.comparingInt(ub -> ub.getBook().getPageCount()))
                .map(ub -> new BookSummaryDto(ub.getBook().getTitle(), ub.getBook().getPageCount()))
                .orElse(null);

        return new StatsDto(
                booksReadAllTime,
                booksReadThisYear,
                currentlyReadingCount,
                goalTarget,
                goalProgressPercent,
                averageRating,
                pagesReadThisYear,
                averageBookLength,
                booksPerMonth,
                longestBook,
                shortestBook
        );
    }

    /**
     * Compute goalProgressPercent with the following business rules (D-08, specifics):
     * <ul>
     *   <li>Returns {@code null} when {@code goalTarget} is null (no goal set)</li>
     *   <li>Returns {@code 100.0} when {@code goalTarget == 0} (divide-by-zero guard,
     *       RESEARCH Pitfall 3 — a goal of 0 is trivially achieved)</li>
     *   <li>Returns {@code Math.min(raw, 100.0)} otherwise — caps at 100 even when overachieved</li>
     * </ul>
     *
     * @param booksReadThisYear count of READ entries finished this year
     * @param goalTarget        the user's target for this year, or null if no goal set
     * @return goalProgressPercent, or null if no goal
     */
    private Double computeGoalProgress(long booksReadThisYear, Integer goalTarget) {
        if (goalTarget == null) {
            return null;
        }
        if (goalTarget == 0) {
            // Goal of 0 is always achieved — return 100 to avoid division by zero
            return 100.0;
        }
        double raw = (booksReadThisYear * 100.0) / goalTarget;
        return Math.min(raw, 100.0);
    }
}
