package com.booktracker.shelf;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure static pace-projection calculator for currently-reading shelf entries
 * (STATS-04). No Spring dependencies — directly unit-testable without a context.
 *
 * <p><strong>Null means "not enough data" (STATS-05), never an exception.</strong>
 * The frontend renders "Not enough data" when {@code estimatedFinishDate} is
 * absent. The guard cascade below returns {@code null} when:
 * <ul>
 *   <li>{@code pageCount} is null or &lt;= 0 — Open Library frequently omits
 *       page counts; unknown book length gives no target (STATS-05)</li>
 *   <li>{@code currentPage} is null or &lt;= 0 — no progress logged yet, so
 *       there is no pace to extrapolate (STATS-05)</li>
 *   <li>{@code dateStarted} or {@code lastReadDate} is null — no pace basis.
 *       Existing entries have null {@code last_read_date} until their next
 *       progress log (V7 is additive-only, no backfill) — they correctly show
 *       "Not enough data" initially (STATS-05)</li>
 *   <li>{@code activeDays < 1} — dateStarted equals lastReadDate, i.e. the
 *       reader "just started" today; a same-day window gives no
 *       pages-per-day denominator (STATS-05)</li>
 * </ul>
 *
 * <p><strong>Effectively finished:</strong> {@code currentPage >= pageCount}
 * short-circuits to {@code today} (resolved Open Question 3 — the shelf card
 * shows "Est. finish: today" rather than suppressing the estimate).
 *
 * <p><strong>Formula (STATS-04):</strong> pagesPerDay = currentPage / activeDays
 * where activeDays = days between dateStarted and lastReadDate
 * ({@link ChronoUnit#DAYS} — never millisecond math). Days remaining =
 * ceil((pageCount - currentPage) / pagesPerDay), rounded UP via
 * {@link Math#ceil} so the estimate never undershoots.
 *
 * <p>Style mirrors {@code ShelfService.applyAutoDateRules}: guard cascade with
 * every rule enumerated in the javadoc.
 */
public final class PaceCalculator {

    /** Static utility — never instantiated. */
    private PaceCalculator() {
    }

    /**
     * Estimate the finish date for a book being read at the reader's observed pace.
     *
     * @param pageCount    total pages in the book (nullable — Open Library data)
     * @param currentPage  the reader's current page (nullable — no progress yet)
     * @param dateStarted  when the reader started the book (nullable)
     * @param lastReadDate the most recent progress-log date (nullable — null for
     *                     entries predating V7 until their next progress log)
     * @param today        the reference date, injected by the caller
     *                     ({@code LocalDate.now()} in production; a fixed literal
     *                     in tests) so this function stays clock-free
     * @return the projected finish date, {@code today} when effectively finished,
     *         or {@code null} when there is not enough data (STATS-05)
     */
    public static LocalDate estimateFinishDate(Integer pageCount, Integer currentPage,
                                               LocalDate dateStarted, LocalDate lastReadDate,
                                               LocalDate today) {
        if (pageCount == null || pageCount <= 0) {
            return null;                                   // STATS-05: unknown book length
        }
        if (currentPage == null || currentPage <= 0) {
            return null;                                   // STATS-05: no progress yet
        }
        if (dateStarted == null || lastReadDate == null) {
            return null;                                   // STATS-05: no pace basis
        }
        long activeDays = ChronoUnit.DAYS.between(dateStarted, lastReadDate);
        if (activeDays < 1) {
            return null;                                   // STATS-05: "just started"
        }
        if (currentPage >= pageCount) {
            return today;                                  // effectively done (Open Question 3)
        }
        double pagesPerDay = (double) currentPage / activeDays;
        long daysRemaining = (long) Math.ceil((pageCount - currentPage) / pagesPerDay);
        return today.plusDays(daysRemaining);              // STATS-04
    }
}
