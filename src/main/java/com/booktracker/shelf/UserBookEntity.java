package com.booktracker.shelf;

import com.booktracker.books.BookEntity;
import com.booktracker.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the {@code user_books} table — represents a single shelf entry:
 * one user's relationship to one book (status, rating, review, progress, dates).
 *
 * <p>The {@code user_books} table is defined in {@code V1__initial_schema.sql} — no
 * Flyway migration is needed for this entity.
 *
 * <p>Column conventions:
 * <ul>
 *   <li>{@code id}           uuid PK → {@link UUID} via {@code GenerationType.UUID} (Hibernate 6)</li>
 *   <li>{@code user_id}      FK → {@link UserEntity} via {@code @ManyToOne(LAZY)}</li>
 *   <li>{@code book_id}      FK → {@link BookEntity} via {@code @ManyToOne(LAZY)}</li>
 *   <li>{@code shelf_status} varchar → {@link ShelfStatus} via {@code @Enumerated(EnumType.STRING)}</li>
 *   <li>{@code rating}       smallint → {@code Integer} (nullable, 1–5)</li>
 *   <li>{@code review}       text → {@code String} (nullable)</li>
 *   <li>{@code current_page} integer → {@code Integer} (nullable)</li>
 *   <li>{@code date_started} date → {@code LocalDate} (nullable)</li>
 *   <li>{@code date_finished} date → {@code LocalDate} (nullable)</li>
 *   <li>{@code created_at}   timestamptz → {@code OffsetDateTime} (immutable, set via @PrePersist)</li>
 * </ul>
 *
 * <p><strong>Unique constraint:</strong> {@code user_books_user_book_uq (user_id, book_id)} —
 * duplicate adds are caught via {@code DataIntegrityViolationException} in {@link ShelfService}.
 *
 * <p><strong>Fetch strategy:</strong> Both {@code @ManyToOne} use {@code FetchType.LAZY} —
 * the list query in {@link ShelfRepository} overrides this with JOIN FETCH for N+1 prevention.
 */
@Entity
@Table(name = "user_books")
public class UserBookEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** FK to the owning user. Nullable=false enforced by schema constraint. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /**
     * FK to the cached book. FetchType.LAZY — overridden by JOIN FETCH in list queries
     * to prevent N+1 (Pitfall 2 in RESEARCH.md).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private BookEntity book;

    /**
     * Reading status stored as VARCHAR (CLAUDE.md: never ORDINAL).
     * Values: WANT_TO_READ, CURRENTLY_READING, READ.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "shelf_status", nullable = false)
    private ShelfStatus shelfStatus;

    /**
     * User rating (1–5). Nullable — user may not have rated the book.
     * Stored as smallint per schema; {@code @Column(columnDefinition = "smallint")} is explicit
     * to match the schema type (Assumption A2 in RESEARCH.md).
     */
    @Column(columnDefinition = "smallint")
    private Integer rating;

    /** Free-text review. Nullable. */
    @Column(columnDefinition = "text")
    private String review;

    /** Current page number. Nullable — null means not started or page not tracked. */
    @Column(name = "current_page")
    private Integer currentPage;

    /**
     * Date user started reading. Nullable.
     * Auto-set to today when status changes to CURRENTLY_READING (D-11) via
     * {@link ShelfService#applyAutoDateRules} — not via @PreUpdate.
     */
    @Column(name = "date_started")
    private LocalDate dateStarted;

    /**
     * Date user finished reading. Nullable.
     * Auto-set to today when status changes to READ (D-10) via
     * {@link ShelfService#applyAutoDateRules}. Cleared when downgrading from READ (D-12).
     */
    @Column(name = "date_finished")
    private LocalDate dateFinished;

    /** Immutable — set at insert time via {@code @PrePersist}. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Sets {@code createdAt} before first persist if not already set. */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    // ----------------------------------------------------------------
    // Getters and setters
    // ----------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public BookEntity getBook() {
        return book;
    }

    public void setBook(BookEntity book) {
        this.book = book;
    }

    public ShelfStatus getShelfStatus() {
        return shelfStatus;
    }

    public void setShelfStatus(ShelfStatus shelfStatus) {
        this.shelfStatus = shelfStatus;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getReview() {
        return review;
    }

    public void setReview(String review) {
        this.review = review;
    }

    public Integer getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(Integer currentPage) {
        this.currentPage = currentPage;
    }

    public LocalDate getDateStarted() {
        return dateStarted;
    }

    public void setDateStarted(LocalDate dateStarted) {
        this.dateStarted = dateStarted;
    }

    public LocalDate getDateFinished() {
        return dateFinished;
    }

    public void setDateFinished(LocalDate dateFinished) {
        this.dateFinished = dateFinished;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
