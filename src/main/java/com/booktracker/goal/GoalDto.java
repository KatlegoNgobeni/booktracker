package com.booktracker.goal;

/**
 * Response DTO for goal endpoints ({@code PUT /api/goal} and {@code GET /api/goal}).
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code id}          — the goal UUID (string form)</li>
 *   <li>{@code targetCount} — the yearly reading target (>= 0)</li>
 *   <li>{@code year}        — the calendar year this goal applies to</li>
 * </ul>
 *
 * <p>Implemented as a Java record for immutability and conciseness.
 * Jackson serializes records correctly via the canonical constructor.
 */
public record GoalDto(
        String id,
        Integer targetCount,
        Integer year
) {}
