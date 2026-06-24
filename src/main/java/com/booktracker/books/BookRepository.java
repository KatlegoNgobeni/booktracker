package com.booktracker.books;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link BookEntity}.
 *
 * <p>Provides the cache-or-fetch lookup: {@link #findByOpenLibraryKey} is used by
 * {@code BookService.getOrFetch()} to check if a book is already cached locally
 * before fetching from Open Library (D-02).
 *
 * <p>The derived finder method maps to the {@code open_library_key} column via
 * the {@code openLibraryKey} field name on {@link BookEntity}.
 *
 * <p>The {@code books_open_library_key_uq} unique constraint in the schema is the
 * atomic guard against concurrent duplicate inserts — {@code DataIntegrityViolationException}
 * is caught and recovered in {@code BookService.getOrFetch()} (Pitfall 7 / TOCTOU-safe).
 */
public interface BookRepository extends JpaRepository<BookEntity, UUID> {

    /**
     * Find a cached book by its Open Library work key.
     *
     * @param openLibraryKey the full Open Library key (e.g. {@code /works/OL45804W})
     * @return the cached {@link BookEntity} if present, or empty Optional on cache miss
     */
    Optional<BookEntity> findByOpenLibraryKey(String openLibraryKey);
}
