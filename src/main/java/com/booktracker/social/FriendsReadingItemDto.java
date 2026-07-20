package com.booktracker.social;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for a single friends-reading feed item ({@code GET /api/feed/friends-reading}).
 *
 * <p>Each item represents one CURRENTLY_READING shelf entry by a user the current user
 * is accepted friends with (bidirectional). It includes both book data and reader identity
 * so the frontend can render:
 * - Which friend is currently reading which book
 * - The book cover, title, and link to the work
 * - The reader's display name and user ID
 *
 * <p>Uses {@code @JsonInclude(NON_NULL)} so nullable fields ({@code bookCoverId},
 * {@code bookAuthors}) are omitted when null.
 *
 * <p>Fields match the {@code FriendsReadingItem} TypeScript interface added in Plan 03:
 * <ul>
 *   <li>{@code entryId}     — user_books UUID (for dedup and click-through)</li>
 *   <li>{@code userId}      — the reader's UUID (for linking to /users/:id)</li>
 *   <li>{@code displayName} — the reader's display name</li>
 *   <li>{@code bookTitle}   — book title</li>
 *   <li>{@code bookOlKey}   — short-form work key (stripped of "/works/" prefix)</li>
 *   <li>{@code bookCoverId} — Open Library cover ID (nullable)</li>
 *   <li>{@code bookAuthors} — comma-joined authors (nullable)</li>
 * </ul>
 *
 * <p>Does NOT include {@code rating}, {@code review}, or {@code dateFinished} —
 * those fields are not applicable for CURRENTLY_READING entries.
 *
 * <p><strong>Security (T-15-01 / T-15-02):</strong>
 * Content is scoped to the current user's accepted friends only — enforced at the
 * repository layer by {@link FriendRequestRepository#findFriendsCurrentlyReading}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FriendsReadingItemDto {

    private String entryId;
    private String userId;
    private String displayName;
    private String bookTitle;
    private String bookOlKey;
    private String bookCoverId;
    private String bookAuthors;
    private String photoUrl;

    public FriendsReadingItemDto(
            String entryId,
            String userId,
            String displayName,
            String bookTitle,
            String bookOlKey,
            String bookCoverId,
            String bookAuthors,
            String photoUrl) {
        this.entryId = entryId;
        this.userId = userId;
        this.displayName = displayName;
        this.bookTitle = bookTitle;
        this.bookOlKey = bookOlKey;
        this.bookCoverId = bookCoverId;
        this.bookAuthors = bookAuthors;
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

    public String getPhotoUrl() {
        return photoUrl;
    }
}
