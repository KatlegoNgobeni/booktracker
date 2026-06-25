package com.booktracker.shelf;

/**
 * Enum representing the reading status of a book on a user's shelf.
 *
 * <p>Values must exactly match the {@code shelf_status} column values in
 * {@code V1__initial_schema.sql} and the {@code user_books_user_status_idx} index.
 *
 * <p><strong>Convention:</strong> Stored as VARCHAR via {@code @Enumerated(EnumType.STRING)}
 * — never as an ordinal integer (CLAUDE.md constraint).
 *
 * <p>DNF (Did Not Finish) is deferred to v2 per project decision (STATE.md Deferred Items).
 */
public enum ShelfStatus {
    WANT_TO_READ,
    CURRENTLY_READING,
    READ
}
