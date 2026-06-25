package com.booktracker.goal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for {@code PUT /api/goal}.
 *
 * <p>Validates that {@code targetCount} is present and non-negative:
 * <ul>
 *   <li>{@code @NotNull} — missing field returns 400 (T-05-04)</li>
 *   <li>{@code @Min(0)} — negative value returns 400 (T-05-04, D-01)</li>
 * </ul>
 *
 * <p>The DB also enforces {@code check (target_count >= 0)} as a hard guard —
 * the Bean Validation here provides the friendly 400 response before the DB is hit.
 *
 * <p><strong>No year field:</strong> Year is always derived server-side from
 * {@code LocalDate.now().getYear()} (D-01, D-03 — no user-supplied year).
 */
public class SetGoalRequest {

    @NotNull(message = "targetCount is required")
    @Min(value = 0, message = "targetCount must be 0 or greater")
    private Integer targetCount;

    // Default constructor (required for Jackson deserialization)
    public SetGoalRequest() {}

    public Integer getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(Integer targetCount) {
        this.targetCount = targetCount;
    }
}
