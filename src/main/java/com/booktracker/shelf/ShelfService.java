package com.booktracker.shelf;

import com.booktracker.books.BookEntity;
import com.booktracker.books.BookRepository;
import com.booktracker.books.BookService;
import com.booktracker.user.UserEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Business logic for shelf management: add, list, and get-by-id operations.
 *
 * <p><strong>Add flow (SHELF-01):</strong> Delegates to {@code BookService.getOrFetch(olKey)}
 * to ensure the book is cached locally (fetch-and-cache), then resolves the {@link BookEntity}
 * FK via {@link BookRepository#findByOpenLibraryKey}. The full canonical key
 * ({@code /works/OL45804W}) is constructed internally — the request body carries only the
 * short form ({@code OL45804W}).
 *
 * <p><strong>Duplicate handling (D-03 + T-04-02):</strong> The {@code user_books_user_book_uq}
 * unique constraint on {@code (user_id, book_id)} is the atomic guard against duplicate adds.
 * {@link DataIntegrityViolationException} is caught here (not in {@code GlobalExceptionHandler})
 * and thrown as {@code ResponseStatusException(CONFLICT, "Book already on shelf")} to avoid
 * the misleading "Email already registered" message from the global handler (Pitfall 1).
 *
 * <p><strong>N+1 prevention (SHELF-02):</strong> List queries use {@link ShelfRepository}
 * methods with {@code JOIN FETCH ub.book} — no per-row SELECT on the books table.
 *
 * <p><strong>Ownership enforcement (SHELF-06 + T-04-01 IDOR):</strong> Single-entry reads
 * use a two-step pattern in {@link #getEntryForUser}: {@code findById} → 404 if absent;
 * equality check on {@code entry.getUser().getId()} → 403 if mismatch.
 * This is required to properly distinguish 404 from 403 (project decision: 403 for ownership
 * violations, not 404 — STATE.md). Using {@code findByIdAndUser} alone collapses both cases
 * (Pitfall 4 in RESEARCH.md).
 *
 * <p><strong>Transactional import:</strong> Uses {@code org.springframework.transaction.annotation.Transactional}
 * (Spring), NOT {@code jakarta.transaction.Transactional} — avoids Pitfall 6.
 */
@Service
public class ShelfService {

    private final ShelfRepository shelfRepository;
    private final BookService bookService;
    private final BookRepository bookRepository;

    public ShelfService(ShelfRepository shelfRepository,
                        BookService bookService,
                        BookRepository bookRepository) {
        this.shelfRepository = shelfRepository;
        this.bookService = bookService;
        this.bookRepository = bookRepository;
    }

    /**
     * Add a book to the user's shelf.
     *
     * <p>Calls {@code BookService.getOrFetch(olKey)} to ensure the book is in the local cache.
     * On success, resolves the {@link BookEntity} FK via the full canonical key
     * ({@code /works/ + olKey}) and persists a {@link UserBookEntity}.
     *
     * <p>If the book is already on the user's shelf ({@code user_books_user_book_uq} fires),
     * throws {@code 409 Conflict} with message "Book already on shelf" (D-03, T-04-02).
     *
     * @param olKey  short-form Open Library work key (e.g. "OL45804W")
     * @param status initial shelf status
     * @param user   the authenticated user
     * @return DTO representing the created shelf entry
     * @throws ResponseStatusException 409 if the book is already on the shelf
     */
    @Transactional
    public ShelfEntryDto addToShelf(String olKey, ShelfStatus status, UserEntity user) {
        // Ensure the book is cached locally (fetch-and-cache, SHELF-01 / D-01 / D-02).
        // getOrFetch() expects the short form; it prepends /works/ internally.
        bookService.getOrFetch(olKey);

        // Resolve the BookEntity FK — safe because getOrFetch() guarantees presence.
        // The books table stores the full canonical key (/works/OL45804W).
        BookEntity bookEntity = bookRepository.findByOpenLibraryKey("/works/" + olKey)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Book not found after fetch: " + olKey));

        UserBookEntity entry = new UserBookEntity();
        entry.setUser(user);
        entry.setBook(bookEntity);
        entry.setShelfStatus(status);

        try {
            // saveAndFlush forces an immediate SQL INSERT within the transaction so
            // DataIntegrityViolationException fires here (not at transaction commit),
            // allowing us to catch it and return 409 before the GlobalExceptionHandler
            // gets a chance to return "Email already registered" (D-03, Pitfall 1).
            UserBookEntity saved = shelfRepository.saveAndFlush(entry);
            return toDto(saved);
        } catch (DataIntegrityViolationException e) {
            // user_books_user_book_uq constraint fired — duplicate (SHELF-01, D-03).
            // Must NOT reach GlobalExceptionHandler.handleDuplicate (returns "Email already registered").
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Book already on shelf");
        }
    }

    /**
     * List the user's shelf entries, optionally filtered by status.
     *
     * <p>Uses JOIN FETCH queries from {@link ShelfRepository} to prevent N+1 on the
     * book association (SHELF-02). The explicit {@code countQuery} ensures correct
     * pagination metadata with Hibernate 6.
     *
     * @param status   filter by this status, or {@code null} to return all entries
     * @param pageable page/size/sort — default 20 per page, 0-based
     * @param user     the authenticated user
     * @return paginated shelf entries with inline book summary
     */
    public Page<ShelfEntryDto> listShelf(ShelfStatus status, Pageable pageable, UserEntity user) {
        if (status == null) {
            return shelfRepository.findByUser(user, pageable).map(this::toDto);
        } else {
            return shelfRepository.findByUserAndShelfStatus(user, status, pageable).map(this::toDto);
        }
    }

    /**
     * Get a single shelf entry by ID, enforcing ownership.
     *
     * @param id   the shelf entry UUID
     * @param user the authenticated user
     * @return DTO for the shelf entry
     * @throws ResponseStatusException 404 if the entry does not exist
     * @throws ResponseStatusException 403 if the entry belongs to another user (T-04-01 IDOR)
     */
    public ShelfEntryDto getEntry(UUID id, UserEntity user) {
        return toDto(getEntryForUser(id, user));
    }

    /**
     * Two-step ownership check — required to distinguish 404 from 403 (SHELF-06 + T-04-01).
     *
     * <p>Step 1: {@code findById} → throws 404 if absent.
     * Step 2: owner ID equality check → throws 403 if mismatch.
     *
     * <p>Using only {@code findByIdAndUser} would collapse both into an empty Optional,
     * returning 404 even when the entry exists but belongs to another user (Pitfall 4).
     *
     * @param id   the shelf entry UUID
     * @param user the requesting user
     * @return the found {@link UserBookEntity} if it exists and belongs to the user
     */
    private UserBookEntity getEntryForUser(UUID id, UserEntity user) {
        UserBookEntity entry = shelfRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Shelf entry not found"));
        if (!entry.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return entry;
    }

    /**
     * Map a {@link UserBookEntity} to a {@link ShelfEntryDto}.
     *
     * <p>Book summary fields ({@code title}, {@code olKey}, {@code coverId}, {@code authors})
     * are denormalized from the associated {@link BookEntity} (D-08). The book association
     * must be loaded (not a lazy proxy) before calling this method — ensured by JOIN FETCH
     * in the list queries and by the {@code @Transactional} scope on {@code addToShelf}.
     */
    private ShelfEntryDto toDto(UserBookEntity entry) {
        BookEntity book = entry.getBook();
        return new ShelfEntryDto(
                entry.getId().toString(),
                entry.getShelfStatus(),
                entry.getRating(),
                entry.getReview(),
                entry.getCurrentPage(),
                entry.getDateStarted(),
                entry.getDateFinished(),
                entry.getCreatedAt(),
                book.getTitle(),
                book.getOpenLibraryKey(),
                book.getCoverId(),
                book.getAuthors()
        );
    }
}
