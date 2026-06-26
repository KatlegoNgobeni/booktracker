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

import java.time.LocalDate;
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
     * Update metadata on an owned shelf entry (SHELF-03).
     *
     * <p>Applies auto-date rules (D-10/D-11/D-12) via {@link #applyAutoDateRules} BEFORE
     * setting the new status, so the helper can read the old status.
     *
     * <p>Null on any request field means preserve-existing (MVP null-semantics, RESEARCH.md
     * Pattern 8 / Open Question 1).
     *
     * @param id   the shelf entry UUID
     * @param req  partial metadata update — any field may be null (preserve-existing)
     * @param user the authenticated user (ownership enforced via getEntryForUser)
     * @return updated DTO
     * @throws ResponseStatusException 404 if entry not found, 403 if wrong owner
     */
    @Transactional
    public ShelfEntryDto updateMetadata(UUID id, UpdateShelfRequest req, UserEntity user) {
        UserBookEntity entry = getEntryForUser(id, user);

        if (req.getStatus() != null) {
            // D-13: auto-date logic lives here, reads old status BEFORE updating.
            applyAutoDateRules(entry, req.getStatus());
            entry.setShelfStatus(req.getStatus());
        }
        if (req.getRating() != null) {
            entry.setRating(req.getRating().shortValue());
        }
        if (req.getReview() != null) {
            entry.setReview(req.getReview());
        }
        // Explicit dateStarted/dateFinished from the request wins over auto-date rule.
        if (req.getDateStarted() != null) {
            entry.setDateStarted(req.getDateStarted());
        }
        if (req.getDateFinished() != null) {
            entry.setDateFinished(req.getDateFinished());
        }

        shelfRepository.save(entry);
        return toDto(entry);
    }

    /**
     * Update reading progress on an owned shelf entry (SHELF-04).
     *
     * @param id   the shelf entry UUID
     * @param req  progress update with {@code @NotNull @Min(0) currentPage}
     * @param user the authenticated user
     * @return updated DTO
     * @throws ResponseStatusException 404 if entry not found, 403 if wrong owner
     */
    @Transactional
    public ShelfEntryDto updateProgress(UUID id, UpdateProgressRequest req, UserEntity user) {
        UserBookEntity entry = getEntryForUser(id, user);
        entry.setCurrentPage(req.getCurrentPage());
        shelfRepository.save(entry);
        return toDto(entry);
    }

    /**
     * Remove an owned shelf entry (SHELF-05).
     *
     * @param id   the shelf entry UUID
     * @param user the authenticated user
     * @throws ResponseStatusException 404 if entry not found, 403 if wrong owner
     */
    @Transactional
    public void removeEntry(UUID id, UserEntity user) {
        UserBookEntity entry = getEntryForUser(id, user);
        shelfRepository.delete(entry);
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
     * Apply auto-date rules before a status change (D-10, D-11, D-12 — D-13: service only).
     *
     * <p>Must be called BEFORE {@code entry.setShelfStatus(newStatus)} so that the old
     * status can be read via {@code entry.getShelfStatus()}.
     *
     * <ul>
     *   <li>D-10: {@code newStatus == READ} and {@code dateFinished == null} → set to today</li>
     *   <li>D-11: {@code newStatus == CURRENTLY_READING} and {@code dateStarted == null} → set to today</li>
     *   <li>D-12: {@code oldStatus == READ} and {@code newStatus != READ} → clear dateFinished</li>
     * </ul>
     *
     * @param entry     the shelf entry in its current (pre-update) state
     * @param newStatus the status the entry is being changed to
     */
    private void applyAutoDateRules(UserBookEntity entry, ShelfStatus newStatus) {
        ShelfStatus oldStatus = entry.getShelfStatus();

        if (newStatus == ShelfStatus.READ) {
            // D-10: auto-set dateFinished only if not already set
            if (entry.getDateFinished() == null) {
                entry.setDateFinished(LocalDate.now());
            }
        } else if (newStatus == ShelfStatus.CURRENTLY_READING) {
            // D-11: auto-set dateStarted only if not already set
            if (entry.getDateStarted() == null) {
                entry.setDateStarted(LocalDate.now());
            }
        }

        // D-12: downgrade from READ to any other status — clear stale dateFinished
        if (oldStatus == ShelfStatus.READ && newStatus != ShelfStatus.READ) {
            entry.setDateFinished(null);
        }
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
                entry.getRating() != null ? entry.getRating().intValue() : null,
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
