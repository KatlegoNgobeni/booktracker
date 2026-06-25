package com.booktracker.shelf;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for {@code PATCH /shelf/{id}/progress} — reading progress update.
 *
 * <p>Exposed fields: only {@code currentPage} (T-04-08 mass-assignment prevention).
 *
 * <p>Validation:
 * <ul>
 *   <li>{@code currentPage}: {@code @NotNull} — field is required in the request body</li>
 *   <li>{@code currentPage}: {@code @Min(0)} — value 0 is allowed (means "not started");
 *       negative values are rejected with 400 (T-04-07)</li>
 * </ul>
 */
public class UpdateProgressRequest {

    /**
     * Current page the user is on. 0 = not started / reset.
     * Must be non-null and >= 0.
     */
    @NotNull
    @Min(0)
    private Integer currentPage;

    // ----------------------------------------------------------------
    // No-arg constructor (required for Jackson deserialization)
    // ----------------------------------------------------------------

    public UpdateProgressRequest() {
    }

    // ----------------------------------------------------------------
    // Getter and setter
    // ----------------------------------------------------------------

    public Integer getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(Integer currentPage) {
        this.currentPage = currentPage;
    }
}
