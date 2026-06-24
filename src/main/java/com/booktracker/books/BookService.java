package com.booktracker.books;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Business logic for book search (Plan 01) and cache-or-fetch detail retrieval (Plan 02).
 *
 * <p>Search path (D-01): delegates directly to {@link OpenLibraryClient}.
 * No database access on the search path — search results are ephemeral.
 *
 * <p>Detail path (D-02): uses {@link BookRepository#findByOpenLibraryKey} as a local cache.
 * On cache hit, returns the DB record without fetching from Open Library.
 * On cache miss, fetches from Open Library via {@link OpenLibraryClient#getWork}, saves
 * the entity, and returns the DTO.
 *
 * <p>Concurrent-save safety (Pitfall 7): A {@link DataIntegrityViolationException} on
 * {@code bookRepository.save()} (caused by the {@code books_open_library_key_uq} unique
 * constraint under concurrent requests) is caught and recovered by re-running
 * {@link BookRepository#findByOpenLibraryKey}. The second lookup finds the row inserted
 * by the concurrent thread. This follows the TOCTOU-safe pattern established in
 * {@code AuthService} for email uniqueness.
 *
 * <p>This exception is NOT re-thrown to {@code GlobalExceptionHandler} — the existing
 * {@code DataIntegrityViolationException} handler in {@code GlobalExceptionHandler}
 * returns "Email already registered", which is wrong for books.
 */
@Service
public class BookService {

    private final BookRepository bookRepository;
    private final OpenLibraryClient openLibraryClient;

    public BookService(BookRepository bookRepository, OpenLibraryClient openLibraryClient) {
        this.bookRepository = bookRepository;
        this.openLibraryClient = openLibraryClient;
    }

    /**
     * Search Open Library for books matching the query string.
     *
     * <p>Pass-through to {@link OpenLibraryClient#search} — no transformation,
     * no caching, no database write (D-01: search results are ephemeral).
     *
     * @param q    search query forwarded to Open Library
     * @param page 0-based page index
     * @param size number of results per page
     * @return list of search results mapped to {@link BookSearchResultDto}
     */
    public List<BookSearchResultDto> search(String q, int page, int size) {
        return openLibraryClient.search(q, page, size);
    }

    /**
     * Fetch a book's detail — from the local cache (DB) on hit, or from Open Library on miss.
     *
     * <p>Cache hit (D-02): {@code findByOpenLibraryKey} returns present → maps entity to
     * {@link BookDetailDto}; no outbound Open Library call is made.
     *
     * <p>Cache miss: calls {@link OpenLibraryClient#getWork}, maps the response to a
     * {@link BookEntity}, saves it, and returns a {@link BookDetailDto} mapped from the saved entity.
     *
     * <p>Key normalization: the controller receives only the short-form path segment
     * (e.g. {@code OL45804W}). This method prepends {@code /works/} to form the full
     * canonical key (e.g. {@code /works/OL45804W}) used for DB storage and lookup.
     * {@link OpenLibraryClient#getWork} is called with the short form (path variable in
     * {@code /works/{olKey}.json}).
     * The full-path form ({@code /works/OL45804W}) as a URL segment is NOT supported —
     * Spring MVC only captures one path segment for {@code /{olKey}}.
     *
     * <p>Error cases (propagated to the controller):
     * <ul>
     *   <li>Open Library 404 → {@code ResponseStatusException(NOT_FOUND)}</li>
     *   <li>Open Library timeout → {@code ResponseStatusException(SERVICE_UNAVAILABLE)}</li>
     * </ul>
     *
     * <p>Concurrent-save race: {@link DataIntegrityViolationException} on save is caught
     * inside {@link #persistBook}; a second {@code findByOpenLibraryKey} is run to
     * retrieve the row inserted by the concurrent request (TOCTOU recovery).
     *
     * @param olKey the Open Library work key in short form ({@code OL45804W});
     *              the full form ({@code /works/OL45804W}) is NOT accepted here —
     *              it is produced internally before DB operations
     * @return book detail DTO
     * @throws org.springframework.web.server.ResponseStatusException on 404 or timeout
     */
    public BookDetailDto getOrFetch(String olKey) {
        // olKey is the bare short form from the URL segment (e.g. "OL45804W").
        // Prepend /works/ to form the full canonical key used for DB storage and lookup.
        // The URL only ever delivers the short form — Spring MVC /{olKey} captures one segment.
        String fullKey = "/works/" + olKey;

        // Cache hit — return from DB without calling Open Library.
        return bookRepository.findByOpenLibraryKey(fullKey)
                .map(this::toDetailDto)
                .orElseGet(() -> {
                    // HTTP call is intentionally OUTSIDE any transaction (I/O inside a
                    // transaction is an anti-pattern — holds the connection during network I/O).
                    OpenLibraryWorkResponse work = openLibraryClient.getWork(olKey);
                    // Delegate the transactional DB write + TOCTOU recovery to a helper.
                    return persistBook(fullKey, work);
                });
    }

    /**
     * Persist a fetched work to the DB inside a single transaction.
     *
     * <p>On unique-constraint violation (concurrent request already inserted the same key),
     * recovers by re-fetching the row. The original {@link DataIntegrityViolationException}
     * is NOT re-thrown — it would reach {@code GlobalExceptionHandler.handleDuplicate}
     * and return a misleading "Email already registered" 409 response.
     *
     * @param fullKey the full canonical OL key (e.g. {@code /works/OL45804W})
     * @param work    the work response from Open Library
     * @return book detail DTO from the saved (or concurrently inserted) entity
     */
    @Transactional
    BookDetailDto persistBook(String fullKey, OpenLibraryWorkResponse work) {
        try {
            BookEntity saved = bookRepository.save(toEntity(fullKey, work));
            return toDetailDto(saved);
        } catch (DataIntegrityViolationException e) {
            // Concurrent request already inserted this row — recover by re-fetching.
            return bookRepository.findByOpenLibraryKey(fullKey)
                    .map(this::toDetailDto)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Failed to persist book: " + fullKey));
        }
    }

    // ----------------------------------------------------------------
    // Private mapping helpers
    // ----------------------------------------------------------------

    /**
     * Maps a cached {@link BookEntity} to a {@link BookDetailDto}.
     */
    private BookDetailDto toDetailDto(BookEntity entity) {
        return new BookDetailDto(
                entity.getOpenLibraryKey(),
                entity.getTitle(),
                entity.getAuthors(),
                entity.getCoverId(),
                entity.getPageCount(),
                entity.getFirstPublishYear()
        );
    }

    /**
     * Maps an {@link OpenLibraryWorkResponse} to a new {@link BookEntity} ready for persistence.
     *
     * <p>Authors are stored as null — the works endpoint returns author references as objects
     * (not plain strings); resolving them requires additional API calls (out of scope Phase 3).
     * The {@code authors} column is nullable in the V1 schema.
     *
     * <p>Cover ID is taken from the first element of the {@code covers} array (null-guarded),
     * converted to String to match the {@code cover_id varchar} column in the schema.
     */
    private BookEntity toEntity(String olKey, OpenLibraryWorkResponse work) {
        String title = work.getTitle();
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Open Library returned a work without a title: " + olKey);
        }
        BookEntity entity = new BookEntity();
        entity.setOpenLibraryKey(olKey);
        entity.setTitle(title);
        entity.setAuthors(null); // works endpoint returns author objects, not strings — Phase 3 scope
        entity.setPageCount(work.getPageCount());
        entity.setFirstPublishYear(null); // not available on works endpoint write path

        // Null-guard: covers may be absent (empty list or null)
        if (work.getCovers() != null && !work.getCovers().isEmpty()) {
            entity.setCoverId(String.valueOf(work.getCovers().get(0)));
        } else {
            entity.setCoverId(null);
        }

        return entity;
    }
}
