package com.booktracker.shelf;

import com.booktracker.user.UserEntity;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for shelf management — exposes POST /shelf, GET /shelf, and
 * GET /shelf/{id} under the {@code /api} context-path.
 *
 * <p>All endpoints require a valid JWT (covered by {@code anyRequest().authenticated()}
 * in {@code SecurityConfig} — no changes needed to the security config for Phase 4).
 *
 * <p><strong>@AuthenticationPrincipal:</strong> Uses {@code UserEntity} directly (not
 * {@code UserDetails}) because {@code JwtAuthenticationFilter} populates the
 * {@code SecurityContext} with a {@code UserEntity} instance (via
 * {@code userDetailsService.loadUserByUsername(uuid)} which returns {@code UserEntity}).
 * This avoids a redundant DB lookup compared to the {@code UUID.fromString(username)} pattern
 * (Pattern 7 in RESEARCH.md, Pitfall 8).
 */
@RestController
@RequestMapping("/shelf")
public class ShelfController {

    private final ShelfService shelfService;

    public ShelfController(ShelfService shelfService) {
        this.shelfService = shelfService;
    }

    /**
     * POST /shelf — add a book to the authenticated user's shelf.
     *
     * <p>Returns 201 Created + {@link ShelfEntryDto} on success.
     * Returns 400 Bad Request on validation failure (blank olKey, unknown status).
     * Returns 409 Conflict if the book is already on the shelf (D-03).
     *
     * @param request JSON body with {@code olKey} (short form) and {@code status}
     * @param user    the authenticated user (injected from JWT principal)
     * @return 201 + shelf entry DTO
     */
    @PostMapping
    public ResponseEntity<ShelfEntryDto> addToShelf(
            @Valid @RequestBody AddToShelfRequest request,
            @AuthenticationPrincipal UserEntity user) {
        ShelfEntryDto dto = shelfService.addToShelf(request.getOlKey(), request.getStatus(), user);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * GET /shelf — list the authenticated user's shelf entries, paginated.
     *
     * <p>Optional {@code status} query param filters entries by {@link ShelfStatus}.
     * Spring Data {@link Pageable} supports {@code page}, {@code size}, and {@code sort}.
     *
     * @param status   optional filter (WANT_TO_READ, CURRENTLY_READING, READ), or null for all
     * @param pageable page/size/sort from query params (default page=0, size=20)
     * @param user     the authenticated user
     * @return paginated list of shelf entries with inline book summary
     */
    @GetMapping
    public Page<ShelfEntryDto> listShelf(
            @RequestParam(required = false) ShelfStatus status,
            Pageable pageable,
            @AuthenticationPrincipal UserEntity user) {
        return shelfService.listShelf(status, pageable, user);
    }

    /**
     * GET /shelf/{id} — get a single shelf entry by ID.
     *
     * <p>Returns 404 if the entry does not exist.
     * Returns 403 if the entry belongs to another user (T-04-01 IDOR, SHELF-06).
     *
     * @param id   the shelf entry UUID
     * @param user the authenticated user
     * @return the shelf entry DTO
     */
    @GetMapping("/{id}")
    public ShelfEntryDto getEntry(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserEntity user) {
        return shelfService.getEntry(id, user);
    }
}
