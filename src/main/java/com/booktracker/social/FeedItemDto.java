package com.booktracker.social;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * DTO for a single activity feed item ({@code GET /api/feed}).
 *
 * <p>Each feed item represents one READ shelf entry by a user the current user follows.
 * It includes both book data and reader identity so the frontend can render:
 * - Which user finished which book
 * - The book cover, title, and link to the work
 * - Rating and review (nullable — user may not have reviewed)
 * - When the book was finished (for relative timestamps)
 *
 * <p>Uses {@code @JsonInclude(NON_NULL)} so nullable fields ({@code bookCoverId},
 * {@code bookAuthors}, {@code rating}, {@code review}) are omitted when null.
 *
 * <p>Fields match the RESEARCH DTO Field List exactly:
 * <ul>
 *   <li>{@code entryId}      — user_books UUID (for dedup and click-through)</li>
 *   <li>{@code userId}       — the reader's UUID (for linking to /users/:id)</li>
 *   <li>{@code displayName}  — the reader's display name</li>
 *   <li>{@code bookTitle}    — book title</li>
 *   <li>{@code bookOlKey}    — short-form work key (stripped of "/works/" prefix)</li>
 *   <li>{@code bookCoverId}  — Open Library cover ID (nullable)</li>
 *   <li>{@code bookAuthors}  — comma-joined authors (nullable)</li>
 *   <li>{@code rating}       — 1–5 rating (nullable)</li>
 *   <li>{@code review}       — review text (nullable)</li>
 *   <li>{@code dateFinished} — ISO date when user finished reading</li>
 *   <li>{@code createdAt}    — entry creation timestamp (for feed ordering precision)</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeedItemDto {

    private String entryId;
    private String userId;
    private String displayName;
    private String bookTitle;
    private String bookOlKey;
    private String bookCoverId;
    private String bookAuthors;
    private Integer rating;
    private String review;
    private LocalDate dateFinished;
    private OffsetDateTime createdAt;
    private String photoUrl;

    public FeedItemDto(
            String entryId,
            String userId,
            String displayName,
            String bookTitle,
            String bookOlKey,
            String bookCoverId,
            String bookAuthors,
            Integer rating,
            String review,
            LocalDate dateFinished,
            OffsetDateTime createdAt,
            String photoUrl) {
        this.entryId = entryId;
        this.userId = userId;
        this.displayName = displayName;
        this.bookTitle = bookTitle;
        this.bookOlKey = bookOlKey;
        this.bookCoverId = bookCoverId;
        this.bookAuthors = bookAuthors;
        this.rating = rating;
        this.review = review;
        this.dateFinished = dateFinished;
        this.createdAt = createdAt;
        this.photoUrl = photoUrl;
    }

    // ----------------------------------------------------------------
    // Getters
    // ----------------------------------------------------------------

    public String getEntryId() {
        return entryId;
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public String getBookOlKey() {
        return bookOlKey;
    }

    public String getBookCoverId() {
        return bookCoverId;
    }

    public String getBookAuthors() {
        return bookAuthors;
    }

    public Integer getRating() {
        return rating;
    }

    public String getReview() {
        return review;
    }

    public LocalDate getDateFinished() {
        return dateFinished;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }
}
