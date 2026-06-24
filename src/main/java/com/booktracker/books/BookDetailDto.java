package com.booktracker.books;

/**
 * API response record for the book detail endpoint ({@code GET /api/books/{olKey}}).
 *
 * <p>Contains all D-07 fields including {@code pageCount} (excluded from search results
 * by D-06 but required here for Phase 5 stats). The {@code authors} field is a nullable
 * comma-joined String matching the {@code BookEntity.authors} column — distinct from
 * {@code BookSearchResultDto.authors} which is a {@code List<String>} from the Open Library
 * search response.
 *
 * <p>All nullable fields ({@code authors}, {@code coverId}, {@code pageCount},
 * {@code firstPublishYear}) may be {@code null} — Open Library frequently omits them
 * (BOOK-03).
 *
 * @param olKey            the full Open Library work key (e.g. {@code /works/OL45804W})
 * @param title            the book title (never null — required by schema)
 * @param authors          comma-joined author names (nullable)
 * @param coverId          Open Library cover image ID as String (nullable)
 * @param pageCount        number of pages (nullable — often absent from Open Library)
 * @param firstPublishYear first publication year (nullable)
 */
public record BookDetailDto(
        String olKey,
        String title,
        String authors,
        String coverId,
        Integer pageCount,
        Integer firstPublishYear
) {}
