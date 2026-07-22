package com.booktracker.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for the {@code collection_books} table:
 * {@code (collection_id, book_ol_key)}.
 */
@Embeddable
public class CollectionBookId implements Serializable {

    @Column(name = "collection_id")
    private UUID collectionId;

    @Column(name = "book_ol_key")
    private String bookOlKey;

    protected CollectionBookId() {}

    public CollectionBookId(UUID collectionId, String bookOlKey) {
        this.collectionId = collectionId;
        this.bookOlKey = bookOlKey;
    }

    public UUID getCollectionId() { return collectionId; }
    public String getBookOlKey() { return bookOlKey; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CollectionBookId other)) return false;
        return Objects.equals(collectionId, other.collectionId)
                && Objects.equals(bookOlKey, other.bookOlKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collectionId, bookOlKey);
    }
}
