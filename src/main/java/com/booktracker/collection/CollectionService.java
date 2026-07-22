package com.booktracker.collection;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Business logic for the collections feature (COLL-01/02/03/04).
 *
 * <p>Ownership is enforced with 403 (not 404) for all mutations — consistent with
 * the project-wide convention documented in STATE.md.
 *
 * <p>409 on duplicate name uses {@code existsByUserIdAndName} (application-level check)
 * rather than catching DataIntegrityViolationException — the unique constraint
 * (user_id, name) is the authoritative guard, but we surface a meaningful 409 message
 * before hitting the DB constraint.
 */
@Service
public class CollectionService {

    private final CollectionRepository collectionRepository;
    private final CollectionBookRepository collectionBookRepository;

    public CollectionService(CollectionRepository collectionRepository,
                             CollectionBookRepository collectionBookRepository) {
        this.collectionRepository = collectionRepository;
        this.collectionBookRepository = collectionBookRepository;
    }

    /**
     * List all collections owned by the user (summary DTOs, no book lists).
     */
    @Transactional(readOnly = true)
    public List<CollectionDto> getMyCollections(UUID userId) {
        List<CollectionEntity> collections = collectionRepository.findAllByUserId(userId);
        return collections.stream().map(c -> {
            int bookCount = collectionBookRepository.findAllByIdCollectionId(c.getId()).size();
            return toDto(c, bookCount);
        }).collect(Collectors.toList());
    }

    /**
     * Create a new named collection for the user.
     * Returns 409 if a collection with the same name already exists for this user.
     */
    @Transactional
    public CollectionDto createCollection(UUID userId, String name, boolean isPublic) {
        if (collectionRepository.existsByUserIdAndName(userId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A collection with that name already exists");
        }
        CollectionEntity entity = new CollectionEntity();
        entity.setUserId(userId);
        entity.setName(name);
        entity.setPublic(isPublic);
        CollectionEntity saved = collectionRepository.save(entity);
        return toDto(saved, 0);
    }

    /**
     * Rename or toggle isPublic on an owned collection. Returns 403 if not owner.
     */
    @Transactional
    public CollectionDto updateCollection(UUID userId, UUID id, String name, Boolean isPublic) {
        CollectionEntity entity = collectionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));
        if (name != null && !name.isBlank()) {
            entity.setName(name);
        }
        if (isPublic != null) {
            entity.setPublic(isPublic);
        }
        CollectionEntity saved = collectionRepository.save(entity);
        int bookCount = collectionBookRepository.findAllByIdCollectionId(saved.getId()).size();
        return toDto(saved, bookCount);
    }

    /**
     * Delete an owned collection (cascade deletes books). Returns 403 if not owner.
     */
    @Transactional
    public void deleteCollection(UUID userId, UUID id) {
        CollectionEntity entity = collectionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));
        collectionRepository.delete(entity);
    }

    /**
     * Get collection detail including all ol_keys.
     * Returns 403 if the collection is private and the viewer is not the owner.
     * Returns 404 if the collection does not exist.
     */
    @Transactional(readOnly = true)
    public CollectionDetailDto getCollectionDetail(UUID viewerId, UUID id) {
        CollectionEntity entity = collectionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Collection not found"));
        if (!entity.isPublic() && !entity.getUserId().equals(viewerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        List<String> olKeys = collectionBookRepository.findAllByIdCollectionId(id)
                .stream()
                .map(cb -> cb.getId().getBookOlKey())
                .collect(Collectors.toList());
        return toDetailDto(entity, olKeys);
    }

    /**
     * Add a book (by olKey) to an owned collection.
     * Returns 403 if not owner, 404 if collection not found, 409 if already present.
     */
    @Transactional
    public void addBook(UUID userId, UUID collectionId, String olKey) {
        collectionRepository.findByIdAndUserId(collectionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));
        CollectionBookId bookId = new CollectionBookId(collectionId, olKey);
        if (collectionBookRepository.existsById(bookId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Book already in collection");
        }
        collectionBookRepository.save(new CollectionBookEntity(bookId));
    }

    /**
     * Remove a book from an owned collection.
     * Returns 403 if not owner, 404 if the book entry is not found.
     */
    @Transactional
    public void removeBook(UUID userId, UUID collectionId, String olKey) {
        collectionRepository.findByIdAndUserId(collectionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied"));
        int deleted = collectionBookRepository.deleteByIdCollectionIdAndIdBookOlKey(collectionId, olKey);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Book not found in collection");
        }
    }

    // ----------------------------------------------------------------
    // Mapping helpers
    // ----------------------------------------------------------------

    private CollectionDto toDto(CollectionEntity entity, int bookCount) {
        return new CollectionDto(
                entity.getId().toString(),
                entity.getName(),
                entity.isPublic(),
                bookCount,
                entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null
        );
    }

    private CollectionDetailDto toDetailDto(CollectionEntity entity, List<String> olKeys) {
        return new CollectionDetailDto(
                entity.getId().toString(),
                entity.getName(),
                entity.isPublic(),
                olKeys,
                entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null
        );
    }
}
