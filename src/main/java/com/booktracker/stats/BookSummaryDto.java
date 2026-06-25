package com.booktracker.stats;

/**
 * Nested DTO for {@link StatsDto#getLongestBook()} and {@link StatsDto#getShortestBook()}.
 *
 * <p>Carries the title and page count of a single READ book entry so the frontend
 * can display e.g. "The Shining — 447 pages" without a separate API call.
 *
 * <p>Both fields are always non-null when this DTO is constructed — the {@link StatsService}
 * only creates this DTO for entries where {@code book.pageCount IS NOT NULL} (D-10).
 */
public class BookSummaryDto {

    private final String title;
    private final Integer pageCount;

    public BookSummaryDto(String title, Integer pageCount) {
        this.title = title;
        this.pageCount = pageCount;
    }

    public String getTitle() {
        return title;
    }

    public Integer getPageCount() {
        return pageCount;
    }
}
