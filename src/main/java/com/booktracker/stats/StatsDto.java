package com.booktracker.stats;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for {@code GET /api/stats} (STATS-02).
 *
 * <p>{@code @JsonInclude(NON_NULL)} ensures that nullable fields ({@code goalTarget},
 * {@code goalProgressPercent}, {@code averageRating}, {@code pagesReadThisYear},
 * {@code averageBookLength}, {@code longestBook}, {@code shortestBook}) are omitted from the
 * JSON response when null — not serialized as JSON {@code null} values (RESEARCH Pitfall 5).
 *
 * <p><strong>Why NON_NULL not NON_DEFAULT:</strong> {@code NON_DEFAULT} would omit
 * {@code booksReadAllTime=0} and {@code currentlyReadingCount=0} — those fields must always
 * appear. {@code NON_NULL} only suppresses object/wrapper fields when null, leaving primitives
 * ({@code long}, {@code int[]}) unaffected (RESEARCH Pitfall 5).
 *
 * <p><strong>No genreBreakdown field</strong> — omitted entirely per D-05 (no null field,
 * no empty map — just not present). The prohibition is verified by the plan's verification
 * step: {@code grep -r 'genreBreakdown' src/main/java} must exit non-zero.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code booksReadAllTime} — count of READ entries ever; always present (long primitive)</li>
 *   <li>{@code booksReadThisYear} — count of READ entries with date_finished in current year</li>
 *   <li>{@code currentlyReadingCount} — count of CURRENTLY_READING entries</li>
 *   <li>{@code goalTarget} — current year's target_count; null if no goal set (D-08)</li>
 *   <li>{@code goalProgressPercent} — (booksReadThisYear / goalTarget) * 100, capped at 100;
 *       null if no goal; 100.0 if goalTarget == 0 (D-08, specifics, Pitfall 3)</li>
 *   <li>{@code averageRating} — AVG of non-null ratings across READ entries; null if none</li>
 *   <li>{@code pagesReadThisYear} — SUM of page_count for READ entries this year where
 *       page_count IS NOT NULL; null if no qualifying entries (D-09, Pitfall 4 — no COALESCE)</li>
 *   <li>{@code averageBookLength} — AVG page_count across all READ entries with non-null
 *       page_count; null if none</li>
 *   <li>{@code booksPerMonth} — always a 12-element int array (index 0=Jan..11=Dec),
 *       never null; zero-filled for months with no reads (D-08, D-11)</li>
 *   <li>{@code longestBook} — title+pageCount of the READ book with the highest non-null
 *       page_count; null if no qualifying entries (D-10)</li>
 *   <li>{@code shortestBook} — title+pageCount of the READ book with the lowest non-null
 *       page_count; null if no qualifying entries (D-10)</li>
 *   <li>{@code currentStreakDays} — length of the current reading streak ending today or
 *       yesterday (STATS-01); always present — int primitive, so
 *       {@code @JsonInclude(NON_NULL)} never strips it even at 0 (STATS-06)</li>
 *   <li>{@code longestStreakDays} — longest-ever consecutive-day reading streak (STATS-02);
 *       always present — int primitive, same NON_NULL reasoning as
 *       {@code booksReadAllTime} (STATS-06)</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StatsDto {

    private final long booksReadAllTime;
    private final long booksReadThisYear;
    private final long currentlyReadingCount;

    /** Null if no goal set for the current year (D-08). */
    private final Integer goalTarget;

    /** Null if no goal set; capped at 100.0; 100.0 when goalTarget == 0 (D-08). */
    private final Double goalProgressPercent;

    /** Null if no READ entries have a rating (D-08). */
    private final Double averageRating;

    /**
     * Null if no READ entries have a non-null page_count for the current year (D-09, Pitfall 4).
     * Do NOT use COALESCE in the query — the null signals "no qualifying entries."
     */
    private final Long pagesReadThisYear;

    /** Null if no READ entries have a non-null page_count (D-08). */
    private final Double averageBookLength;

    /**
     * Always a 12-element int array; never null. Index 0 = January, index 11 = December.
     * Months with no reads are 0 (D-08, D-11).
     */
    private final int[] booksPerMonth;

    /** Null if no READ entries have a non-null page_count (D-10). */
    private final BookSummaryDto longestBook;

    /** Null if no READ entries have a non-null page_count (D-10). */
    private final BookSummaryDto shortestBook;

    /**
     * Current reading streak in days, ending today or yesterday (STATS-01).
     * Primitive int — always serialized, 0 when no live streak (STATS-06).
     */
    private final int currentStreakDays;

    /**
     * Longest-ever reading streak in days (STATS-02).
     * Primitive int — always serialized, 0 when no activity (STATS-06).
     */
    private final int longestStreakDays;

    /**
     * Most common first-subject-token among this year's READ books with non-null subjects.
     * Null if no subject data is available yet — absent from JSON when null per class-level
     * {@code @JsonInclude(NON_NULL)} (STATS-08).
     * Mutable (not final) so {@link StatsService} can set it after construction.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String topGenre;

    public StatsDto(long booksReadAllTime,
                    long booksReadThisYear,
                    long currentlyReadingCount,
                    Integer goalTarget,
                    Double goalProgressPercent,
                    Double averageRating,
                    Long pagesReadThisYear,
                    Double averageBookLength,
                    int[] booksPerMonth,
                    BookSummaryDto longestBook,
                    BookSummaryDto shortestBook,
                    int currentStreakDays,
                    int longestStreakDays) {
        this.booksReadAllTime = booksReadAllTime;
        this.booksReadThisYear = booksReadThisYear;
        this.currentlyReadingCount = currentlyReadingCount;
        this.goalTarget = goalTarget;
        this.goalProgressPercent = goalProgressPercent;
        this.averageRating = averageRating;
        this.pagesReadThisYear = pagesReadThisYear;
        this.averageBookLength = averageBookLength;
        this.booksPerMonth = booksPerMonth;
        this.longestBook = longestBook;
        this.shortestBook = shortestBook;
        this.currentStreakDays = currentStreakDays;
        this.longestStreakDays = longestStreakDays;
    }

    public long getBooksReadAllTime() {
        return booksReadAllTime;
    }

    public long getBooksReadThisYear() {
        return booksReadThisYear;
    }

    public long getCurrentlyReadingCount() {
        return currentlyReadingCount;
    }

    public Integer getGoalTarget() {
        return goalTarget;
    }

    public Double getGoalProgressPercent() {
        return goalProgressPercent;
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public Long getPagesReadThisYear() {
        return pagesReadThisYear;
    }

    public Double getAverageBookLength() {
        return averageBookLength;
    }

    public int[] getBooksPerMonth() {
        return booksPerMonth;
    }

    public BookSummaryDto getLongestBook() {
        return longestBook;
    }

    public BookSummaryDto getShortestBook() {
        return shortestBook;
    }

    public int getCurrentStreakDays() {
        return currentStreakDays;
    }

    public int getLongestStreakDays() {
        return longestStreakDays;
    }

    public String getTopGenre() {
        return topGenre;
    }

    public void setTopGenre(String topGenre) {
        this.topGenre = topGenre;
    }
}
