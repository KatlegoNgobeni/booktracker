package com.booktracker.books;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Business logic for book search (Plan 01) and detail retrieval (Plan 02).
 *
 * <p>Plan 01 — search path only: delegates directly to {@link OpenLibraryClient}.
 * No database access on the search path per D-01 (search is not persisted).
 * Plan 02 will add {@code BookRepository} constructor injection for the
 * shelf-management (add-to-shelf, progress update) paths.
 */
@Service
public class BookService {

    private final OpenLibraryClient openLibraryClient;

    public BookService(OpenLibraryClient openLibraryClient) {
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
}
