package com.booktracker.shelf;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

/**
 * Request DTO for {@code PATCH /shelf/{id}} — metadata update.
 *
 * <p>All fields are nullable. A {@code null} value means "preserve the existing value"
 * (null = no-op, per MVP decision in RESEARCH.md Pattern 8 / Open Question 1).
 *
 * <p>Exposed fields match the threat model (T-04-08): only status, rating, review,
 * dateStarted, and dateFinished. Jackson ignores unknown fields by default, preventing
 * mass-assignment of other entity properties (user, book, id).
 *
 * <p>Validation:
 * <ul>
 *   <li>{@code rating}: {@code @Min(1) @Max(5)} — applied only when non-null (T-04-09)</li>
 *   <li>{@code status}: enum deserialization returns 400 on unknown value (T-04-10)</li>
 * </ul>
 */
public class UpdateShelfRequest {

    /** Reading status to set. Null = preserve existing. */
    private ShelfStatus status;

    /**
     * User rating, 1–5. Null = preserve existing.
     * Validation applied at {@code @Valid} processing time.
     */
    @Min(1)
    @Max(5)
    private Integer rating;

    /** Free-text review. Null = preserve existing. */
    private String review;

    /** Date the user started reading. Null = preserve existing. */
    private LocalDate dateStarted;

    /** Date the user finished reading. Null = preserve existing. */
    private LocalDate dateFinished;

    // ----------------------------------------------------------------
    // No-arg constructor (required for Jackson deserialization)
    // ----------------------------------------------------------------

    public UpdateShelfRequest() {
    }

    // ----------------------------------------------------------------
    // Getters and setters
    // ----------------------------------------------------------------

    public ShelfStatus getStatus() {
        return status;
    }

    public void setStatus(ShelfStatus status) {
        this.status = status;
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
}
