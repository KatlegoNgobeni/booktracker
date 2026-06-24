package com.booktracker.books;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the {@code books} table — maps Open Library work-level data
 * cached on first detail fetch (D-02).
 *
 * <p>The {@code books} table is defined in {@code V1__initial_schema.sql} — no
 * Flyway migration is needed for this entity. Column names and nullability mirror
 * the V1 schema exactly.
 *
 * <p>Column mapping (from V1__initial_schema.sql):
 * <ul>
 *   <li>{@code id}                  uuid PK    → {@link UUID} via {@code GenerationType.UUID}</li>
 *   <li>{@code open_library_key}    varchar NN  → {@code String openLibraryKey} (unique, not null)</li>
 *   <li>{@code title}               varchar NN  → {@code String title} (not null)</li>
 *   <li>{@code authors}             varchar     → {@code String authors} (nullable, comma-joined)</li>
 *   <li>{@code cover_id}            varchar     → {@code String coverId} (nullable; integer stored as String)</li>
 *   <li>{@code page_count}          integer     → {@code Integer pageCount} (nullable)</li>
 *   <li>{@code first_publish_year}  integer     → {@code Integer firstPublishYear} (nullable)</li>
 *   <li>{@code created_at}          timestamptz → {@code OffsetDateTime createdAt} (immutable)</li>
 * </ul>
 *
 * <p><strong>UUID strategy:</strong> {@code GenerationType.UUID} (Hibernate 6).
 * The {@code "uuid2"} strategy from Hibernate 5 is removed and must not be used.
 *
 * <p><strong>Namespace:</strong> {@code jakarta.persistence.*} — Spring Boot 3.x (CLAUDE.md).
 *
 * <p><strong>cover_id as varchar:</strong> Open Library's {@code cover_i} is an integer,
 * but the schema stores it as {@code varchar}. The service converts via {@code String.valueOf()}.
 */
@Entity
@Table(name = "books")
public class BookEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** The Open Library work key (e.g. {@code /works/OL45804W}). Unique per schema constraint. */
    @Column(name = "open_library_key", nullable = false, unique = true)
    private String openLibraryKey;

    @Column(nullable = false)
    private String title;

    /** Comma-joined author names — nullable (authors absent on the works detail write path). */
    @Column
    private String authors;

    /** Raw Open Library cover integer ID stored as String — nullable (cover absent for many works). */
    @Column(name = "cover_id")
    private String coverId;

    /** Page count from the work detail response — nullable (frequently absent). */
    @Column(name = "page_count")
    private Integer pageCount;

    /** First publication year — nullable (absent on the work detail write path). */
    @Column(name = "first_publish_year")
    private Integer firstPublishYear;

    /** Immutable — set at insert time via {@code @PrePersist}. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Sets {@code createdAt} before first persist if not already set. */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    // ----------------------------------------------------------------
    // Getters and setters
    // ----------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOpenLibraryKey() {
        return openLibraryKey;
    }

    public void setOpenLibraryKey(String openLibraryKey) {
        this.openLibraryKey = openLibraryKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthors() {
        return authors;
    }

    public void setAuthors(String authors) {
        this.authors = authors;
    }

    public String getCoverId() {
        return coverId;
    }

    public void setCoverId(String coverId) {
        this.coverId = coverId;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public Integer getFirstPublishYear() {
        return firstPublishYear;
    }

    public void setFirstPublishYear(Integer firstPublishYear) {
        this.firstPublishYear = firstPublishYear;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
