package com.booktracker.books;

import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
 * rule (T-03-01 mitigation — unauthenticated access denied at the filter level).
 *
 * <p>{@code @Validated} on the class enables method-level constraint validation
 * ({@code @NotBlank} on request params). A blank {@code q} raises
 * {@code ConstraintViolationException}, which {@link com.booktracker.GlobalExceptionHandler}
 * maps to 400 Bad Request.
 */
@RestController
@RequestMapping("/books")
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
     * @param size page size (default 10)
     * @return list of matching books mapped to the D-06 DTO shape
     */
    @GetMapping("/search")
    public List<BookSearchResultDto> search(
            @RequestParam @NotBlank String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return bookService.search(q, page, size);
    }
}
