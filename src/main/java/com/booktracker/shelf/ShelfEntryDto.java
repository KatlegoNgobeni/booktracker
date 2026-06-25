package com.booktracker.shelf;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Response DTO for shelf entry endpoints — returned by POST /shelf, GET /shelf,
 * GET /shelf/{id}, PATCH /shelf/{id}, and PATCH /shelf/{id}/progress.
 *
 * <p>Includes shelf fields and an inline book summary (D-08) to avoid N+1 round-trips
 * on the list endpoint. The book summary fields are denormalized from the associated
 * {@link com.booktracker.books.BookEntity}.
 *
 * <p>All book summary fields ({@code coverId}, {@code authors}) are nullable — Open Library
 * does not guarantee their presence.
 *
 * <p>{@code entryId} is the UUID as a String (consistent with other DTOs in this project).
 * {@code olKey} is the full-form key (e.g. {@code /works/OL45804W}).
 */
public record ShelfEntryDto(
        String entryId,
        ShelfStatus status,
        Integer rating,
        String review,
        Integer currentPage,
        LocalDate dateStarted,
        LocalDate dateFinished,
        OffsetDateTime createdAt,
        // Inline book summary (D-08)
        String title,
        String olKey,
        String coverId,
        String authors
) {}
