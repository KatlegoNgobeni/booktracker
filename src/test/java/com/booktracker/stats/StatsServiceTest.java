package com.booktracker.stats;

import com.booktracker.books.BookEntity;
import com.booktracker.goal.GoalEntity;
import com.booktracker.goal.GoalRepository;
import com.booktracker.shelf.ShelfRepository;
import com.booktracker.shelf.ShelfStatus;
import com.booktracker.shelf.UserBookEntity;
import com.booktracker.user.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StatsService} using plain Mockito — no Spring context.
 *
 * <p>Covers all STATS-02 DTO assembly cases: booksReadAllTime, booksReadThisYear,
 * goalProgressPercent (null/cap-at-100/divide-by-zero guard), pagesReadThisYear
 * (null when no qualifying entries), booksPerMonth (12-element array), and
 * longestBook/shortestBook (null when no entries with page_count).
 *
 * <p>Uses manual constructor injection of {@link StatsService} rather than
 * {@code @InjectMocks} because StatsService uses constructor injection.
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private ShelfRepository shelfRepository;

    @Mock
    private GoalRepository goalRepository;

    private StatsService statsService;

    private UserEntity user;
    private UUID userId;
    private int currentYear;

    @BeforeEach
    void setUp() {
        statsService = new StatsService(shelfRepository, goalRepository);

        user = new UserEntity();
        userId = UUID.randomUUID();
        user.setId(userId);

        currentYear = LocalDate.now().getYear();
    }

    // ----------------------------------------------------------------
    // booksReadAllTime
    // ----------------------------------------------------------------

    /**
     * STATS-02: booksReadAllTime reflects the countBooksReadAllTime repository value.
     */
    @Test
    void booksReadAllTime_reflectsRepositoryCount() {
        stubDefaults();
        when(shelfRepository.countBooksReadAllTime(userId)).thenReturn(7L);

        StatsDto result = statsService.getStats(user);

        assertThat(result.getBooksReadAllTime()).isEqualTo(7L);
    }

    // ----------------------------------------------------------------
    // booksReadThisYear
    // ----------------------------------------------------------------

    /**
     * STATS-02: booksReadThisYear reflects countBooksReadThisYear for the current year only.
     */
    @Test
    void booksReadThisYear_reflectsCurrentYearCount() {
        stubDefaults();
        when(shelfRepository.countBooksReadThisYear(userId, currentYear)).thenReturn(3L);

        StatsDto result = statsService.getStats(user);

        assertThat(result.getBooksReadThisYear()).isEqualTo(3L);
    }

    // ----------------------------------------------------------------
    // goalProgressPercent
    // ----------------------------------------------------------------

    /**
     * STATS-02: goalProgressPercent is capped at 100.0 when booksReadThisYear (12) exceeds
     * goalTarget (10) — never returns 120.
     */
    @Test
    void goalProgressPercent_cappedAt100WhenOverachieved() {
        stubDefaults();
        when(shelfRepository.countBooksReadThisYear(userId, currentYear)).thenReturn(12L);

        GoalEntity goal = buildGoal(10);
        when(goalRepository.findByUserIdAndYear(userId, currentYear)).thenReturn(Optional.of(goal));

        StatsDto result = statsService.getStats(user);

        assertThat(result.getGoalProgressPercent()).isEqualTo(100.0);
    }

    /**
     * STATS-02: goalProgressPercent is 100.0 (not Infinity or NaN) when goalTarget == 0.
     * Division by zero must be guarded explicitly.
     */
    @Test
    void goalProgressPercent_is100WhenGoalTargetIsZero() {
        stubDefaults();
        when(shelfRepository.countBooksReadThisYear(userId, currentYear)).thenReturn(0L);

        GoalEntity goal = buildGoal(0);
        when(goalRepository.findByUserIdAndYear(userId, currentYear)).thenReturn(Optional.of(goal));

        StatsDto result = statsService.getStats(user);

        assertThat(result.getGoalProgressPercent()).isEqualTo(100.0);
        assertThat(result.getGoalProgressPercent()).isFinite();
    }

    /**
     * STATS-02: goalProgressPercent is null when GoalRepository returns empty (no goal set).
     */
    @Test
    void goalProgressPercent_isNullWhenNoGoalSet() {
        stubDefaults();
        when(goalRepository.findByUserIdAndYear(userId, currentYear)).thenReturn(Optional.empty());

        StatsDto result = statsService.getStats(user);

        assertThat(result.getGoalProgressPercent()).isNull();
        assertThat(result.getGoalTarget()).isNull();
    }

    // ----------------------------------------------------------------
    // pagesReadThisYear
    // ----------------------------------------------------------------

    /**
     * STATS-02 + D-09: pagesReadThisYear is null when the repository sum returns null
     * (all book.page_count null / no qualifying entries — Pitfall 4: no COALESCE).
     */
    @Test
    void pagesReadThisYear_isNullWhenRepositorySumReturnsNull() {
        stubDefaults();
        when(shelfRepository.sumPagesReadThisYear(userId, currentYear)).thenReturn(null);

        StatsDto result = statsService.getStats(user);

        assertThat(result.getPagesReadThisYear()).isNull();
    }

    // ----------------------------------------------------------------
    // booksPerMonth
    // ----------------------------------------------------------------

    /**
     * STATS-02 + D-11: booksPerMonth is a 12-element int array. A READ entry finished in
     * March increments index 2; months with no reads are 0.
     */
    @Test
    void booksPerMonth_is12ElementArrayWithCorrectBucketing() {
        stubDefaults();

        // One book finished in March of the current year
        LocalDate marchDate = LocalDate.of(currentYear, 3, 15);
        UserBookEntity marchBook = buildReadEntry(300, marchDate);
        when(shelfRepository.findReadEntriesThisYear(userId, currentYear))
                .thenReturn(List.of(marchBook));

        StatsDto result = statsService.getStats(user);

        assertThat(result.getBooksPerMonth()).hasSize(12);
        assertThat(result.getBooksPerMonth()[2]).isEqualTo(1);  // March = index 2
        assertThat(result.getBooksPerMonth()[0]).isEqualTo(0);  // January = 0
        assertThat(result.getBooksPerMonth()[11]).isEqualTo(0); // December = 0
    }

    // ----------------------------------------------------------------
    // longestBook / shortestBook
    // ----------------------------------------------------------------

    /**
     * STATS-02 + D-10: longestBook and shortestBook are null when
     * findReadEntriesWithPageCount returns an empty list (no qualifying entries).
     */
    @Test
    void longestAndShortestBook_areNullWhenNoEntriesWithPageCount() {
        stubDefaults();
        when(shelfRepository.findReadEntriesWithPageCount(userId)).thenReturn(List.of());

        StatsDto result = statsService.getStats(user);

        assertThat(result.getLongestBook()).isNull();
        assertThat(result.getShortestBook()).isNull();
    }

    /**
     * STATS-02 + D-10: longestBook and shortestBook carry the title and pageCount of the
     * READ book with the max and min page_count respectively.
     */
    @Test
    void longestAndShortestBook_carryTitleAndPageCount() {
        stubDefaults();

        UserBookEntity short1 = buildReadEntry(150, LocalDate.of(currentYear - 1, 6, 1));
        short1.getBook().setTitle("Short Book");

        UserBookEntity long1 = buildReadEntry(700, LocalDate.of(currentYear - 1, 7, 1));
        long1.getBook().setTitle("Long Book");

        UserBookEntity medium = buildReadEntry(350, LocalDate.of(currentYear - 1, 8, 1));
        medium.getBook().setTitle("Medium Book");

        when(shelfRepository.findReadEntriesWithPageCount(userId))
                .thenReturn(List.of(short1, long1, medium));

        StatsDto result = statsService.getStats(user);

        assertThat(result.getLongestBook()).isNotNull();
        assertThat(result.getLongestBook().getTitle()).isEqualTo("Long Book");
        assertThat(result.getLongestBook().getPageCount()).isEqualTo(700);

        assertThat(result.getShortestBook()).isNotNull();
        assertThat(result.getShortestBook().getTitle()).isEqualTo("Short Book");
        assertThat(result.getShortestBook().getPageCount()).isEqualTo(150);
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /**
     * Stub all repository methods to safe defaults (zeros, empty lists, nulls)
     * so tests only need to override the specific behavior under test.
     */
    private void stubDefaults() {
        when(shelfRepository.countBooksReadAllTime(userId)).thenReturn(0L);
        when(shelfRepository.countBooksReadThisYear(userId, currentYear)).thenReturn(0L);
        when(shelfRepository.countCurrentlyReading(userId)).thenReturn(0L);
        when(shelfRepository.sumPagesReadThisYear(userId, currentYear)).thenReturn(null);
        when(shelfRepository.avgRating(userId)).thenReturn(null);
        when(shelfRepository.avgBookLength(userId)).thenReturn(null);
        when(shelfRepository.findReadEntriesThisYear(userId, currentYear)).thenReturn(List.of());
        when(shelfRepository.findReadEntriesWithPageCount(userId)).thenReturn(List.of());
        when(goalRepository.findByUserIdAndYear(userId, currentYear)).thenReturn(Optional.empty());
    }

    /**
     * Build a READ UserBookEntity with the given pageCount and dateFinished.
     */
    private UserBookEntity buildReadEntry(int pageCount, LocalDate dateFinished) {
        BookEntity book = new BookEntity();
        book.setId(UUID.randomUUID());
        book.setOpenLibraryKey("/works/OL" + UUID.randomUUID() + "W");
        book.setTitle("Book " + pageCount);
        book.setPageCount(pageCount);

        UserBookEntity ub = new UserBookEntity();
        ub.setId(UUID.randomUUID());
        ub.setUser(user);
        ub.setBook(book);
        ub.setShelfStatus(ShelfStatus.READ);
        ub.setDateFinished(dateFinished);
        return ub;
    }

    /**
     * Build a GoalEntity with the given targetCount for the current year.
     */
    private GoalEntity buildGoal(int targetCount) {
        GoalEntity goal = new GoalEntity();
        goal.setId(UUID.randomUUID());
        goal.setUser(user);
        goal.setYear(currentYear);
        goal.setTargetCount(targetCount);
        return goal;
    }
}
