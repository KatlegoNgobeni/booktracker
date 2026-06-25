package com.booktracker.shelf;

import com.booktracker.user.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
