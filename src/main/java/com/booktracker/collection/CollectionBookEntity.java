package com.booktracker.collection;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * JPA entity for the {@code collection_books} table (V12 migration).
 * Composite PK: (collection_id, book_ol_key) via {@link CollectionBookId}.
 */
@Entity
@Table(name = "collection_books")
public class CollectionBookEntity {

    @EmbeddedId
    private CollectionBookId id;

    @Column(name = "added_at", nullable = false, updatable = false)
    private OffsetDateTime addedAt;

    protected CollectionBookEntity() {}

    public CollectionBookEntity(CollectionBookId id) {
        this.id = id;
    }

    @PrePersist
    protected void onCreate() {
        if (addedAt == null) {
            addedAt = OffsetDateTime.now();
        }
    }

    public CollectionBookId getId() { return id; }
    public void setId(CollectionBookId id) { this.id = id; }

    public OffsetDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(OffsetDateTime addedAt) { this.addedAt = addedAt; }
}
