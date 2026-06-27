package com.booktracker.books;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for book search (Plan 01) and detail retrieval (Plan 02).
 *
 * <p>Mapped under {@code /books} (becomes {@code /api/books} via context-path).
 * All endpoints require a valid JWT — enforced by
 * {@code SecurityConfig.anyRequest().authenticated()} with no additional permit
 * rule (T-03-01/T-03-08 mitigation — unauthenticated access denied at the filter level).
 *
 * <p>{@code @Validated} on the class enables method-level constraint validation
 * ({@code @NotBlank} on request params). A blank {@code q} raises
 * {@code ConstraintViolationException}, which {@link com.booktracker.GlobalExceptionHandler}
 * maps to 400 Bad Request.
 *
 * <p>The {@code olKey} path variable in the detail endpoint is passed as a URI-template
 * variable to {@link OpenLibraryClient#getWork} — never concatenated into a URL string
 * (T-03-05 SSRF mitigation). The {@code olKey} URL segment must be the bare short form
 * (e.g. {@code OL45804W}), NOT the full path form ({@code /works/OL45804W}).
 * The DB stores the full form internally; {@link BookService#getOrFetch} prepends
 * {@code /works/} before querying or persisting.
 */
@RestController
@RequestMapping("/api/books")
@Validated
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    /**
     * GET /api/books/search — search Open Library by query string.
     *
     * <p>Paginated via {@code page} (0-based, default 0) and {@code size} (default 10).
     * {@code q} must not be blank — a blank value returns 400 Bad Request (BOOK-01, D-03).
     * Response shape is defined by {@link BookSearchResultDto}; no {@code pageCount} field
     * is included (D-06).
     *
     * @param q    non-blank search query
     * @param page 0-based page index (default 0)
     * @param size page size (default 10, min 1, max 100)
     * @return list of matching books mapped to the D-06 DTO shape
     */
    @GetMapping("/search")
    public List<BookSearchResultDto> search(
            @RequestParam @NotBlank String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return bookService.search(q, page, size);
    }

    /**
     * GET /api/books/{olKey} — return detail for a single book; fetch and cache on miss.
     *
     * <p>On cache miss, fetches from Open Library and persists a row to the {@code books} table
     * (D-02). On cache hit, returns the local DB record without re-fetching (D-02).
     *
     * <p>Error responses:
     * <ul>
     *   <li>404 — the {@code olKey} does not exist in Open Library</li>
     *   <li>503 — Open Library is temporarily unreachable (connect/read timeout)</li>
     * </ul>
     *
     * <p>Spring auto-maps the propagated {@code ResponseStatusException} from
     * {@link OpenLibraryClient#getWork} to the correct HTTP status — no handler change needed
     * in {@link com.booktracker.GlobalExceptionHandler}.
     *
     * @param olKey the Open Library work key in short form (e.g. {@code OL45804W}),
     *              NOT the full path form — the full form is only used for DB storage
     * @return book detail DTO with all D-07 fields
     */
    @GetMapping("/{olKey}")
    public BookDetailDto detail(@PathVariable String olKey) {
        return bookService.getOrFetch(olKey);
    }
}
