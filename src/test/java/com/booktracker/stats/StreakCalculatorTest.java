package com.booktracker.stats;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StreakCalculator} — pure static functions, no Spring
 * context, no Mockito (nothing to mock).
 *
 * <p>Covers STATS-01 (current streak ending today or yesterday), STATS-02
 * (longest-ever streak), and STATS-06 (no activity → 0, never null or an error).
 *
 * <p>All dates are fixed literals — never {@code LocalDate.now()} — because
 * {@code today} is a parameter on {@code currentStreak} (clock injected at the
 * call site by StatsService).
 *
 * <p>Input contract (per {@code ReadingActivityRepository.findActivityDatesDesc}):
 * dates are distinct and sorted DESC (newest first).
 */
class StreakCalculatorTest {

    /** Fixed reference date — tests never depend on the wall clock. */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 9);

    // ----------------------------------------------------------------
    // Empty input (STATS-06)
    // ----------------------------------------------------------------

    /**
     * STATS-06: A user with no reading activity gets 0 from both functions —
     * no exception, no null.
     */
    @Test
    void currentStreakAndLongestStreak_emptyDates_returnZero() {
        // Arrange
        List<LocalDate> dates = List.of();

        // Act + Assert
        assertThat(StreakCalculator.currentStreak(dates, TODAY)).isZero();
        assertThat(StreakCalculator.longestStreak(dates)).isZero();
    }

    // ----------------------------------------------------------------
    // currentStreak (STATS-01)
    // ----------------------------------------------------------------

    /**
     * STATS-01: Three consecutive activity days ending today → current streak of 3.
     */
    @Test
    void currentStreak_threeConsecutiveDaysEndingToday_returnsThree() {
        // Arrange: DESC order — today, yesterday, day before
        List<LocalDate> dates = List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2));

        // Act
        int result = StreakCalculator.currentStreak(dates, TODAY);

        // Assert
        assertThat(result).isEqualTo(3);
    }

    /**
     * STATS-01 (yesterday grace): Nothing logged yet today, but activity on
     * yesterday and the day before keeps the streak alive → 2.
     */
    @Test
    void currentStreak_lastActivityYesterday_streakStaysAlive() {
        // Arrange: most recent activity is yesterday
        List<LocalDate> dates = List.of(TODAY.minusDays(1), TODAY.minusDays(2));

        // Act
        int result = StreakCalculator.currentStreak(dates, TODAY);

        // Assert
        assertThat(result).isEqualTo(2);
    }

    /**
     * STATS-06: Most recent activity is before yesterday — the streak is broken
     * and currentStreak returns 0 (not an error).
     */
    @Test
    void currentStreak_lastActivityBeforeYesterday_returnsZero() {
        // Arrange: only activity was two days ago
        List<LocalDate> dates = List.of(TODAY.minusDays(2));

        // Act
        int result = StreakCalculator.currentStreak(dates, TODAY);

        // Assert
        assertThat(result).isZero();
    }

    /**
     * STATS-01: A gap in the run stops the current streak count — activity on
     * today and yesterday, then a gap at today-2, then more history → 2.
     */
    @Test
    void currentStreak_gapBreaksRun_countsOnlyConsecutiveHead() {
        // Arrange: gap at TODAY-2 breaks the run
        List<LocalDate> dates = List.of(
                TODAY, TODAY.minusDays(1), TODAY.minusDays(3), TODAY.minusDays(4));

        // Act
        int result = StreakCalculator.currentStreak(dates, TODAY);

        // Assert
        assertThat(result).isEqualTo(2);
    }

    // ----------------------------------------------------------------
    // longestStreak (STATS-02)
    // ----------------------------------------------------------------

    /**
     * STATS-02: A 5-day historical run separated by a gap from a 2-day current
     * run → longestStreak is 5 (the longest ever, not the current one).
     */
    @Test
    void longestStreak_historicalRunLongerThanCurrent_returnsHistoricalLength() {
        // Arrange: 2-day current run, gap, then a 5-day run (DESC order)
        List<LocalDate> dates = List.of(
                TODAY, TODAY.minusDays(1),
                TODAY.minusDays(10), TODAY.minusDays(11), TODAY.minusDays(12),
                TODAY.minusDays(13), TODAY.minusDays(14));

        // Act
        int result = StreakCalculator.longestStreak(dates);

        // Assert
        assertThat(result).isEqualTo(5);
    }

    // ----------------------------------------------------------------
    // Single date
    // ----------------------------------------------------------------

    /**
     * STATS-01 + STATS-02: A single activity date (today) → both current and
     * longest streak are 1.
     */
    @Test
    void currentStreakAndLongestStreak_singleDateToday_returnOne() {
        // Arrange
        List<LocalDate> dates = List.of(TODAY);

        // Act + Assert
        assertThat(StreakCalculator.currentStreak(dates, TODAY)).isEqualTo(1);
        assertThat(StreakCalculator.longestStreak(dates)).isEqualTo(1);
    }
}
