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
 * <p>{@code ABANDONED} represents Did Not Finish (DNF) and is stored as the string
 * {@code "ABANDONED"} via {@code @Enumerated(EnumType.STRING)} on the {@code UserBookEntity} field.
 */
public enum ShelfStatus {
    WANT_TO_READ,
    CURRENTLY_READING,
    READ,
    ABANDONED
}
