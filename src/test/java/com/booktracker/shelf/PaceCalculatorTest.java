package com.booktracker.shelf;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PaceCalculator} — pure static function, no Spring context,
 * no Mockito (nothing to mock).
 *
 * <p>Covers STATS-04 (estimated finish date from pages-per-day pace) and STATS-05
 * (null result — never an exception — whenever the data is insufficient):
 * <ul>
 *   <li>Guard cascade: null/zero pageCount, null/zero currentPage, null dates</li>
 *   <li>"Just started" guard: dateStarted == lastReadDate → activeDays &lt; 1 → null</li>
 *   <li>Effectively-finished short-circuit: currentPage &gt;= pageCount → today</li>
 *   <li>Happy path: linear pages-per-day extrapolation</li>
 *   <li>Ceiling: fractional days remaining always round UP</li>
 * </ul>
 *
 * <p>All dates are fixed {@link LocalDate} literals — never wall clock — so tests
 * are deterministic regardless of when they run.
 */
class PaceCalculatorTest {

    /** Fixed "today" injected into every call — never LocalDate.now(). */
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 1);
    private static final LocalDate STARTED = LocalDate.of(2026, 6, 21);

    /**
     * STATS-05: pageCount null (Open Library often omits it) or zero → null,
     * meaning "not enough data" — never an exception.
     */
    @Test
    void estimateFinishDate_pageCountNullOrZero_returnsNull() {
        assertThat(PaceCalculator.estimateFinishDate(null, 100, STARTED, TODAY, TODAY)).isNull();
        assertThat(PaceCalculator.estimateFinishDate(0, 100, STARTED, TODAY, TODAY)).isNull();
    }

    /**
     * STATS-05: currentPage null (no progress logged) or zero (no pages read yet)
     * → null — there is no pace to extrapolate from.
     */
    @Test
    void estimateFinishDate_currentPageNullOrZero_returnsNull() {
        assertThat(PaceCalculator.estimateFinishDate(300, null, STARTED, TODAY, TODAY)).isNull();
        assertThat(PaceCalculator.estimateFinishDate(300, 0, STARTED, TODAY, TODAY)).isNull();
    }

    /**
     * STATS-05: dateStarted null or lastReadDate null → null. Existing entries
     * have null last_read_date until their next progress log (V7 is additive-only,
     * no backfill) — they must show "Not enough data", not an error.
     */
    @Test
    void estimateFinishDate_missingDates_returnsNull() {
        assertThat(PaceCalculator.estimateFinishDate(300, 100, null, TODAY, TODAY)).isNull();
        assertThat(PaceCalculator.estimateFinishDate(300, 100, STARTED, null, TODAY)).isNull();
    }

    /**
     * STATS-05 "just started": dateStarted equals lastReadDate → activeDays &lt; 1
     * → null (a same-day start gives no denominator for pages-per-day).
     */
    @Test
    void estimateFinishDate_startedAndLastReadSameDay_returnsNull() {
        LocalDate sameDay = LocalDate.of(2026, 7, 1);
        assertThat(PaceCalculator.estimateFinishDate(300, 50, sameDay, sameDay, TODAY)).isNull();
    }

    /**
     * currentPage &gt;= pageCount → effectively finished → returns today
     * (resolved Open Question 3: show "Est. finish: today", never suppress).
     */
    @Test
    void estimateFinishDate_currentPageAtOrBeyondPageCount_returnsToday() {
        assertThat(PaceCalculator.estimateFinishDate(300, 300, STARTED, TODAY, TODAY)).isEqualTo(TODAY);
        assertThat(PaceCalculator.estimateFinishDate(300, 350, STARTED, TODAY, TODAY)).isEqualTo(TODAY);
    }

    /**
     * STATS-04 happy path: 300 pages, 100 read over 10 active days
     * (2026-06-21 → 2026-07-01) → 10 pages/day → 200 remaining → 20 days
     * → today.plusDays(20) = 2026-07-21.
     */
    @Test
    void estimateFinishDate_evenPace_extrapolatesLinearly() {
        LocalDate result = PaceCalculator.estimateFinishDate(300, 100, STARTED, TODAY, TODAY);
        assertThat(result).isEqualTo(LocalDate.of(2026, 7, 21));
    }

    /**
     * Ceiling: 190 pages, 90 read over 30 active days (2026-06-01 → 2026-07-01)
     * → 3 pages/day → 100 remaining → 100/3 = 33.3 days → rounds UP via
     * Math.ceil to 34 days (never 33 — an estimate must not undershoot).
     */
    @Test
    void estimateFinishDate_unevenRemainder_roundsUpViaCeil() {
        LocalDate started = LocalDate.of(2026, 6, 1);
        LocalDate result = PaceCalculator.estimateFinishDate(190, 90, started, TODAY, TODAY);
        assertThat(result).isEqualTo(TODAY.plusDays(34));
    }
}
