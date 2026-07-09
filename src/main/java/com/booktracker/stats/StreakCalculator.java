package com.booktracker.stats;

import java.time.LocalDate;
import java.util.List;

/**
 * Pure static streak computation over activity-date lists (STATS-01, STATS-02, STATS-06).
 *
 * <p><strong>Input contract (both methods):</strong> {@code dates} must be distinct
 * and sorted DESC (newest first) — exactly the shape returned by
 * {@code ReadingActivityRepository.findActivityDatesDesc}. The composite PK
 * {@code (user_id, activity_date)} guarantees distinctness; the query guarantees order.
 *
 * <p><strong>Clock injection:</strong> {@code today} is a parameter on
 * {@link #currentStreak(List, LocalDate)} — this class never reads the wall clock,
 * so streak math is fully unit-testable with fixed date literals (no Spring, no mocks).
 * StatsService injects {@code LocalDate.now()} at the call site.
 *
 * <p><strong>No dependencies</strong> beyond {@code java.time} and {@code java.util} —
 * no Spring annotations, no Jakarta imports.
 */
public final class StreakCalculator {

    private StreakCalculator() {
        // Utility class — no instances
    }

    /**
     * Length of the user's current reading streak, in days.
     *
     * <p>Business rules:
     * <ul>
     *   <li>STATS-01: the streak "ends today or yesterday" — activity yesterday
     *       (with nothing logged yet today) keeps the streak alive</li>
     *   <li>STATS-06: returns {@code 0} when there is no activity, or when the most
     *       recent activity is before yesterday (streak broken) — never null,
     *       never an exception</li>
     *   <li>Counts consecutive-day links scanning the DESC list from the head;
     *       the first gap ends the count</li>
     * </ul>
     *
     * @param dates distinct activity dates sorted DESC (newest first)
     * @param today the reference date (inject {@code LocalDate.now()} at the call site)
     * @return the current streak length in days, or 0 if no live streak
     */
    public static int currentStreak(List<LocalDate> dates, LocalDate today) {
        if (dates.isEmpty()) {
            return 0;
        }
        LocalDate head = dates.get(0);
        // STATS-01: streak "ends today or yesterday" — yesterday keeps it alive
        if (head.isBefore(today.minusDays(1))) {
            return 0; // STATS-06: 0, not an error
        }
        int streak = 1;
        for (int i = 1; i < dates.size(); i++) {
            if (dates.get(i).equals(dates.get(i - 1).minusDays(1))) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    /**
     * Length of the user's longest-ever reading streak, in days.
     *
     * <p>Business rules:
     * <ul>
     *   <li>STATS-02: scans the entire DESC list tracking the longest consecutive
     *       run — historical runs count even if the current streak is shorter or dead</li>
     *   <li>STATS-06: returns {@code 0} for an empty list — never null,
     *       never an exception</li>
     * </ul>
     *
     * @param dates distinct activity dates sorted DESC (newest first)
     * @return the longest consecutive-day run ever, or 0 if no activity
     */
    public static int longestStreak(List<LocalDate> dates) {
        int longest = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate d : dates) {
            run = (prev != null && d.equals(prev.minusDays(1))) ? run + 1 : 1;
            longest = Math.max(longest, run);
            prev = d;
        }
        return longest;
    }
}
