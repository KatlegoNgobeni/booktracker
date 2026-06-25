package com.booktracker.shelf;

import com.booktracker.user.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link UserBookEntity}.
 *
 * <p>All list queries use {@code JOIN FETCH ub.book} to prevent N+1 queries
 * on the book association (Pitfall 2 in RESEARCH.md — SHELF-02).
 *
 * <p><strong>countQuery is mandatory:</strong> Hibernate 6 cannot derive a count query
 * from a {@code JOIN FETCH} JPQL automatically. Without an explicit {@code countQuery},
 * Hibernate logs a warning and may perform in-memory pagination with incorrect totals
 * (Pitfall 3 in RESEARCH.md).
 *
 * <p><strong>Ownership check (SHELF-06):</strong> Single-entry reads use the inherited
 * {@code findById(UUID)} in a two-step pattern in {@link ShelfService#getEntryForUser}
 * to properly distinguish 404 (entry absent) from 403 (wrong owner).
 * Using {@code findByIdAndUser} alone would collapse both into an empty Optional.
 */
public interface ShelfRepository extends JpaRepository<UserBookEntity, UUID> {

    /**
     * Paginated list of all shelf entries for a user.
     *
     * <p>JOIN FETCH on {@code ub.book} ensures the book data is loaded in a single SQL
     * query — no per-row SELECT on the books table. The explicit {@code countQuery}
     * is required for correct pagination metadata with Hibernate 6.
     *
     * @param user     the authenticated user (must not be null)
     * @param pageable page/size/sort — default size 20, page 0-based
     * @return paginated shelf entries with book data pre-loaded
     */
    @Query(value = "SELECT ub FROM UserBookEntity ub JOIN FETCH ub.book WHERE ub.user = :user",
           countQuery = "SELECT COUNT(ub) FROM UserBookEntity ub WHERE ub.user = :user")
    Page<UserBookEntity> findByUser(@Param("user") UserEntity user, Pageable pageable);

    /**
     * Paginated list of shelf entries for a user filtered by status.
     *
     * <p>Maps to the {@code user_books_user_status_idx (user_id, shelf_status)} composite
     * index defined in V1 — avoids a full scan when filtering by status.
     *
     * @param user     the authenticated user
     * @param status   the shelf status to filter by (must not be null)
     * @param pageable page/size/sort
     * @return paginated shelf entries matching the status with book data pre-loaded
     */
    @Query(value = "SELECT ub FROM UserBookEntity ub JOIN FETCH ub.book " +
                   "WHERE ub.user = :user AND ub.shelfStatus = :status",
           countQuery = "SELECT COUNT(ub) FROM UserBookEntity ub " +
                        "WHERE ub.user = :user AND ub.shelfStatus = :status")
    Page<UserBookEntity> findByUserAndShelfStatus(
            @Param("user") UserEntity user,
            @Param("status") ShelfStatus status,
            Pageable pageable);

    // ----------------------------------------------------------------
    // Stats aggregation queries (STATS-02 / Phase 5)
    //
    // Single-repository-per-entity convention (RESEARCH Open Question 1 / Pitfall 2):
    // Stats aggregation queries are added here rather than creating a second
    // JpaRepository<UserBookEntity, UUID> for stats, which would produce two
    // Spring Data proxy beans for the same entity.
    //
    // Native queries are used for all date-extraction operations to avoid
    // JPQL FUNCTION() portability issues with PostgreSQL (RESEARCH Pitfall 1 /
    // Open Question 2 — PostgreSQL has no year() function; use EXTRACT(YEAR FROM ...) instead).
    //
    // All queries are scoped by userId from @AuthenticationPrincipal — no userId
    // is accepted from the client (T-05-02 IDOR mitigation).
    // ----------------------------------------------------------------

    /**
     * Count all READ shelf entries for the user (booksReadAllTime).
     *
     * @param userId the authenticated user's UUID
     * @return total count of READ entries ever
     */
    @Query("SELECT COUNT(ub) FROM UserBookEntity ub " +
           "WHERE ub.user.id = :userId AND ub.shelfStatus = 'READ'")
    long countBooksReadAllTime(@Param("userId") UUID userId);

    /**
     * Count READ entries where date_finished falls in the given calendar year (booksReadThisYear).
     *
     * <p>Uses native SQL with {@code EXTRACT(YEAR FROM ...)} to avoid JPQL date-function
     * portability issues — PostgreSQL has no {@code year()} function (RESEARCH Pitfall 1).
     *
     * @param userId the authenticated user's UUID
     * @param year   the calendar year (e.g. 2026)
     * @return count of READ entries finished in that year
     */
    @Query(value = "SELECT COUNT(*) FROM user_books ub " +
                   "WHERE ub.user_id = :userId AND ub.shelf_status = 'READ' " +
                   "AND EXTRACT(YEAR FROM ub.date_finished) = :year",
           nativeQuery = true)
    long countBooksReadThisYear(@Param("userId") UUID userId, @Param("year") int year);

    /**
     * Count CURRENTLY_READING entries for the user.
     *
     * @param userId the authenticated user's UUID
     * @return count of CURRENTLY_READING entries
     */
    @Query("SELECT COUNT(ub) FROM UserBookEntity ub " +
           "WHERE ub.user.id = :userId AND ub.shelfStatus = 'CURRENTLY_READING'")
    long countCurrentlyReading(@Param("userId") UUID userId);

    /**
     * Sum page_count for READ entries finished in the given year where page_count IS NOT NULL.
     *
     * <p><strong>Returns null (not 0) when no qualifying rows</strong> — this is the desired
     * signal for "no entries with a non-null page_count this year" (STATS-02 D-09, RESEARCH
     * Pitfall 4). Do NOT use COALESCE here; the null is semantically meaningful.
     *
     * <p>Return type is {@code Long} (nullable wrapper) so JPA returns {@code null} rather
     * than throwing when the aggregation result is null.
     *
     * @param userId the authenticated user's UUID
     * @param year   the calendar year (e.g. 2026)
     * @return sum of page counts, or null if no qualifying entries
     */
    @Query(value = "SELECT SUM(b.page_count) FROM user_books ub " +
                   "JOIN books b ON ub.book_id = b.id " +
                   "WHERE ub.user_id = :userId AND ub.shelf_status = 'READ' " +
                   "AND EXTRACT(YEAR FROM ub.date_finished) = :year " +
                   "AND b.page_count IS NOT NULL",
           nativeQuery = true)
    Long sumPagesReadThisYear(@Param("userId") UUID userId, @Param("year") int year);

    /**
     * Average rating across all READ entries with a non-null rating.
     *
     * <p>Returns null when no READ entries have a rating (JPQL AVG on empty set → null).
     *
     * @param userId the authenticated user's UUID
     * @return average rating, or null if no ratings
     */
    @Query("SELECT AVG(ub.rating) FROM UserBookEntity ub " +
           "WHERE ub.user.id = :userId AND ub.shelfStatus = 'READ' AND ub.rating IS NOT NULL")
    Double avgRating(@Param("userId") UUID userId);

    /**
     * Average page_count across all READ entries where page_count IS NOT NULL.
     *
     * <p>Returns null when no qualifying entries exist.
     *
     * @param userId the authenticated user's UUID
     * @return average book length in pages, or null if no qualifying entries
     */
    @Query(value = "SELECT AVG(b.page_count) FROM user_books ub " +
                   "JOIN books b ON ub.book_id = b.id " +
                   "WHERE ub.user_id = :userId AND ub.shelf_status = 'READ' " +
                   "AND b.page_count IS NOT NULL",
           nativeQuery = true)
    Double avgBookLength(@Param("userId") UUID userId);

    /**
     * Load all READ entries whose date_finished falls in the given year, with book pre-loaded.
     *
     * <p>JOIN FETCH prevents N+1 queries when {@link com.booktracker.stats.StatsService}
     * iterates the list to build the {@code booksPerMonth} array.
     *
     * <p>Uses native SQL with {@code EXTRACT(YEAR FROM ...)} for date extraction
     * (RESEARCH Pitfall 1 / Open Question 2).
     *
     * @param userId the authenticated user's UUID
     * @param year   the calendar year
     * @return READ entries finished in that year with book loaded
     */
    @Query("SELECT ub FROM UserBookEntity ub JOIN FETCH ub.book " +
           "WHERE ub.user.id = :userId AND ub.shelfStatus = 'READ' " +
           "AND ub.dateFinished IS NOT NULL " +
           "AND FUNCTION('date_part', 'year', ub.dateFinished) = :year")
    List<UserBookEntity> findReadEntriesThisYear(@Param("userId") UUID userId, @Param("year") int year);

    /**
     * Load all READ entries where book.pageCount IS NOT NULL, with book pre-loaded.
     *
     * <p>Used by {@link com.booktracker.stats.StatsService} to compute longestBook and
     * shortestBook via Java stream max/min (D-10). Returns entries from all years (not
     * just this year) — longestBook/shortestBook are all-time statistics.
     *
     * @param userId the authenticated user's UUID
     * @return READ entries with non-null page_count, book pre-loaded
     */
    @Query("SELECT ub FROM UserBookEntity ub JOIN FETCH ub.book " +
           "WHERE ub.user.id = :userId AND ub.shelfStatus = 'READ' " +
           "AND ub.book.pageCount IS NOT NULL")
    List<UserBookEntity> findReadEntriesWithPageCount(@Param("userId") UUID userId);
}
