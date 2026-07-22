package com.booktracker.collection;

import com.booktracker.user.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for the collections feature (COLL-01/02/03/04).
 *
 * <p>GET /api/collections/{id} is publicly accessible (SecurityConfig permits it) —
 * the service enforces private-collection 403 for non-owners.
 *
 * <p>All mutations require JWT auth (covered by SecurityConfig /api/** authenticated rule).
 * userId is always sourced from @AuthenticationPrincipal — never from the request body.
 */
@RestController
@RequestMapping("/api/collections")
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    /**
     * GET /api/collections — list the authenticated user's collections.
     */
    @GetMapping
    public List<CollectionDto> getMyCollections(@AuthenticationPrincipal UserEntity user) {
        return collectionService.getMyCollections(user.getId());
    }

    /**
     * POST /api/collections — create a new named collection.
     * Body: { name, isPublic }
     */
    @PostMapping
    public ResponseEntity<CollectionDto> createCollection(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserEntity user) {
        String name = (String) body.get("name");
        boolean isPublic = body.containsKey("isPublic") && Boolean.TRUE.equals(body.get("isPublic"));
        CollectionDto dto = collectionService.createCollection(user.getId(), name, isPublic);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * GET /api/collections/{id} — get collection detail (public or owned).
     * Unauthenticated callers get a null viewerId — service returns 403 for private collections.
     */
    @GetMapping("/{id}")
    public CollectionDetailDto getCollectionDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserEntity user) {
        UUID viewerId = user != null ? user.getId() : null;
        return collectionService.getCollectionDetail(viewerId, id);
    }

    /**
     * PATCH /api/collections/{id} — rename or toggle isPublic on an owned collection.
     * Body: { name?, isPublic? }
     */
    @PatchMapping("/{id}")
    public CollectionDto updateCollection(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserEntity user) {
        String name = (String) body.get("name");
        Boolean isPublic = body.containsKey("isPublic") ? (Boolean) body.get("isPublic") : null;
        return collectionService.updateCollection(user.getId(), id, name, isPublic);
    }

    /**
     * DELETE /api/collections/{id} — delete an owned collection (cascade deletes books).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCollection(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserEntity user) {
        collectionService.deleteCollection(user.getId(), id);
    }

    /**
     * POST /api/collections/{id}/books — add a book by olKey to an owned collection.
     * Body: { olKey }
     */
    @PostMapping("/{id}/books")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addBook(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserEntity user) {
        String olKey = (String) body.get("olKey");
        collectionService.addBook(user.getId(), id, olKey);
    }

    /**
     * DELETE /api/collections/{id}/books/{olKey} — remove a book from an owned collection.
     */
    @DeleteMapping("/{id}/books/{olKey}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeBook(
            @PathVariable UUID id,
            @PathVariable String olKey,
            @AuthenticationPrincipal UserEntity user) {
        collectionService.removeBook(user.getId(), id, olKey);
    }
}
