package com.booktracker.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link UserEntity}.
 *
 * <p>Provides standard CRUD operations (via {@link JpaRepository}) plus a
 * derived query to look up a user by their unique email address, and a
 * JPQL search query for the user discovery People tab (DISC-01).
 *
 * <p>Used by {@code AuthService.register()} to detect duplicate-email attempts
 * (indirectly — via DB constraint + {@code DataIntegrityViolationException}),
 * and by {@code JwtAuthenticationFilter} via {@code UserDetailsService}.
 */
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Finds a user by their email address.
     *
     * @param email the email address to look up (case-sensitive — DB stores as-is)
     * @return an {@link Optional} containing the user if found, or empty
     */
    Optional<UserEntity> findByEmail(String email);

    /**
     * Search for users by display name (case-insensitive LIKE), excluding the current user.
     *
     * <p>Used by the People tab in SearchPage (DISC-01, D-05).
     * Results are ordered by {@code displayName ASC} for consistent pagination.
     *
     * <p><strong>T-09-04 SQL Injection mitigation:</strong> Uses JPQL parameterized query
     * with {@code :query} bound via {@code @Param} — never string concatenation.
     *
     * @param query         the search string (case-insensitive, prefix/suffix wildcard via CONCAT)
     * @param currentUserId UUID of the authenticated user — excluded from results
     * @param pageable      page/size for the results
     * @return paginated users whose displayName contains {@code query}, excluding {@code currentUserId}
     */
    @Query("SELECT u FROM UserEntity u " +
           "WHERE LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "AND u.id <> :currentUserId " +
           "ORDER BY u.displayName ASC")
    Page<UserEntity> searchByDisplayName(@Param("query") String query,
                                         @Param("currentUserId") UUID currentUserId,
                                         Pageable pageable);
}
