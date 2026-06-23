package com.booktracker.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link UserEntity}.
 *
 * <p>Provides standard CRUD operations (via {@link JpaRepository}) plus a
 * derived query to look up a user by their unique email address.
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
}
