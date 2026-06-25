package com.booktracker.shelf;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /shelf — adds a book to the authenticated user's shelf.
 *
 * <p>{@code olKey} is the short-form Open Library work key (e.g. {@code OL45804W}),
 * NOT the full form ({@code /works/OL45804W}). {@link ShelfService#addToShelf} passes
 * it directly to {@code BookService.getOrFetch()}, which prepends {@code /works/} internally.
 *
 * <p>Validation:
 * <ul>
 *   <li>{@code olKey} — {@code @NotBlank} → 400 if blank or missing (T-04-05)</li>
 *   <li>{@code status} — {@code @NotNull} → 400 if null; Jackson returns 400 on
 *       unknown enum value automatically (T-04-03)</li>
 * </ul>
 */
public class AddToShelfRequest {

    /** Short-form Open Library work key (e.g. "OL45804W"). NOT the full "/works/OL45804W" form. */
    @NotBlank
    private String olKey;

    /** Shelf status for the entry. Must be one of {@link ShelfStatus} values. */
    @NotNull
    private ShelfStatus status;

    public AddToShelfRequest() {}

    public String getOlKey() {
        return olKey;
    }

    public void setOlKey(String olKey) {
        this.olKey = olKey;
    }

    public ShelfStatus getStatus() {
        return status;
    }

    public void setStatus(ShelfStatus status) {
        this.status = status;
    }
}
