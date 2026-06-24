package com.booktracker.books;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

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
     * <p>Error cases (propagated to the controller):
     * <ul>
     *   <li>Open Library 404 → {@code ResponseStatusException(NOT_FOUND)}</li>
     *   <li>Open Library timeout → {@code ResponseStatusException(SERVICE_UNAVAILABLE)}</li>
     * </ul>
     *
     * <p>Concurrent-save race: {@link DataIntegrityViolationException} on save is caught;
     * a second {@code findByOpenLibraryKey} is run to retrieve the row inserted by the
     * concurrent request (TOCTOU recovery — see class Javadoc).
     *
     * @param olKey the Open Library work key (e.g. {@code /works/OL45804W})
     * @return book detail DTO
     * @throws org.springframework.web.server.ResponseStatusException on 404 or timeout
     */
    public BookDetailDto getOrFetch(String olKey) {
        return bookRepository.findByOpenLibraryKey(olKey)
                .map(this::toDetailDto)
                .orElseGet(() -> {
                    OpenLibraryWorkResponse work = openLibraryClient.getWork(olKey);
                    BookEntity entity = toEntity(olKey, work);
                    try {
                        BookEntity saved = bookRepository.save(entity);
                        return toDetailDto(saved);
                    } catch (DataIntegrityViolationException e) {
                        // Concurrent request already inserted this row — recover by re-fetching.
                        // This must NOT propagate to GlobalExceptionHandler (wrong message for books).
                        return bookRepository.findByOpenLibraryKey(olKey)
                                .map(this::toDetailDto)
                                .orElseThrow(() -> e);
                    }
                });
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
        BookEntity entity = new BookEntity();
        entity.setOpenLibraryKey(olKey);
        entity.setTitle(work.getTitle());
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
