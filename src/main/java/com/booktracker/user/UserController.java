package com.booktracker.user;

import com.booktracker.social.FriendRequestService;
import com.booktracker.social.UserSearchResultDto;
import org.apache.tika.Tika;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller for user profile, search, and photo upload endpoints.
 *
 * <p>Mapped under {@code /api/users}. All endpoints require a valid JWT
 * (covered by {@code SecurityConfig.anyRequest().authenticated()}).
 *
 * <p><strong>Phase 17 additions:</strong> {@code POST /me/photo} and
 * {@code DELETE /me/photo} for profile photo upload and removal.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final FriendRequestService friendRequestService;

    public UserController(UserService userService, FriendRequestService friendRequestService) {
        this.userService = userService;
        this.friendRequestService = friendRequestService;
    }

    /**
     * GET /api/users/me — returns the authenticated user's own profile (AUTH-03, D-10).
     *
     * <p>{@code @AuthenticationPrincipal UserDetails} is injected by Spring Security from
     * the {@code SecurityContext} — populated by {@code JwtAuthenticationFilter} on each
     * authenticated request.
     *
     * <p>The principal's username is the user's UUID string (D-06:
     * {@code UserEntity.getUsername()} returns {@code id.toString()}).
     *
     * @param userDetails the authenticated principal (never null on a protected endpoint)
     * @return DTO with id, email, displayName, createdAt, photoUrl — no password field (D-10)
     */
    @GetMapping("/me")
    public UserResponseDto me(@AuthenticationPrincipal UserDetails userDetails) {
        // D-06: username = UUID string (from UserEntity.getUsername())
        UUID userId = UUID.fromString(userDetails.getUsername());
        return userService.getUserById(userId);
    }

    /**
     * PATCH /api/users/me — update the authenticated user's display name and/or password.
     *
     * <p>All fields are optional; only supplied (non-null) fields are applied.
     * Password change requires {@code currentPassword} to match the stored BCrypt hash.
     *
     * @param request     partial-update payload (validated)
     * @param currentUser the authenticated user (from JWT principal)
     * @return updated UserResponseDto
     */
    @PatchMapping("/me")
    public ResponseEntity<UserResponseDto> updateMe(
            @RequestBody @Valid UpdateUserRequest request,
            @AuthenticationPrincipal UserEntity currentUser) {
        UserResponseDto result = userService.updateUser(currentUser.getId(), request);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/users/search?q=name — search for users by display name (DISC-01, D-05).
     *
     * <p>Returns a paginated list of {@link UserSearchResultDto} for users whose
     * {@code displayName} contains {@code q} (case-insensitive), excluding the current user.
     * Each result includes a {@code friendStatus} derived from the friend_requests table
     * in a single bulk query — no N+1 (RESEARCH Open Question 2).
     *
     * <p><strong>T-09-04 (SQL injection):</strong> The {@code q} parameter is passed to
     * JPQL as a bind parameter ({@code :query}) — never concatenated.
     *
     * <p>The current user's identity comes from {@code @AuthenticationPrincipal UserEntity}
     * — never from a request parameter (T-09-03 Spoofing mitigation).
     *
     * @param q           required search string (case-insensitive display name fragment)
     * @param pageable    page/size for the results (default page=0, size=20)
     * @param currentUser the authenticated user (from JWT principal)
     * @return paginated UserSearchResultDto list ordered by displayName ASC
     */
    @GetMapping("/search")
    public Page<UserSearchResultDto> searchUsers(
            @RequestParam String q,
            Pageable pageable,
            @AuthenticationPrincipal UserEntity currentUser) {
        return friendRequestService.searchUsers(q, pageable, currentUser);
    }

    /**
     * POST /api/users/me/photo — upload or replace the authenticated user's profile photo (PHOTO-01, PHOTO-03).
     *
     * <p>Validation steps (in order):
     * <ol>
     *   <li>Empty file check — returns 400 Bad Request</li>
     *   <li>Size check: {@code > 5 MB} — returns 400 Bad Request (T-17-02 / D-05)</li>
     *   <li>Magic-byte MIME detection via Apache Tika — returns 415 Unsupported Media Type
     *       for non-image files (T-17-01 / PHOTO-03). Tika reads actual bytes; the
     *       {@code Content-Type} header is NOT used for validation.</li>
     * </ol>
     *
     * <p>Allowed MIME types include {@code image/heic} and {@code image/heif} to support
     * iOS devices whose camera default format is HEIC (Research Pitfall 6).
     *
     * <p>Bytes are buffered once ({@code file.getBytes()}) and reused for both Tika
     * and the Cloudinary upload — avoids the consumed-InputStream pitfall (Research Pitfall 4).
     *
     * <p><strong>T-17-04 (IDOR):</strong> The userId comes from
     * {@code @AuthenticationPrincipal} — never from request body or params.
     *
     * @param file        the uploaded image file (multipart/form-data field name: "file")
     * @param currentUser the authenticated user (from JWT principal)
     * @return 200 OK with updated UserResponseDto containing the new photoUrl
     * @throws IOException if bytes cannot be read from the multipart input (propagated to 500)
     */
    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserResponseDto> uploadPhoto(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserEntity currentUser) throws IOException {

        // Empty file guard
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }

        // Size gate — T-17-02 / D-05: 5 MB hard limit before reading bytes
        if (file.getSize() > 5L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo must be under 5 MB");
        }

        // Buffer bytes once — reused for Tika check AND Cloudinary upload (Research Pitfall 4)
        byte[] bytes = file.getBytes();

        // Magic-byte MIME detection — T-17-01 / PHOTO-03
        // Tika reads actual file bytes; getContentType() is NOT used (spoofable header)
        String mime = new Tika().detect(new ByteArrayInputStream(bytes));
        Set<String> allowed = Set.of(
                "image/jpeg", "image/png", "image/gif", "image/webp",
                "image/heic", "image/heif"  // HEIC/HEIF for iOS camera uploads (Pitfall 6)
        );
        if (!allowed.contains(mime)) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only image files are accepted");
        }

        UserResponseDto result = userService.uploadProfilePhoto(currentUser.getId(), bytes);
        return ResponseEntity.ok(result);
    }

    /**
     * DELETE /api/users/me/photo — remove the authenticated user's profile photo (PHOTO-04).
     *
     * <p>Sets {@code profile_photo_url = null} in the DB. Returns the updated
     * {@link UserResponseDto} with {@code photoUrl: null} so the client can
     * immediately revert the avatar to the Dicebear fallback (D-11).
     *
     * <p><strong>T-17-04 (IDOR):</strong> The userId comes from
     * {@code @AuthenticationPrincipal} — never from request params.
     *
     * @param currentUser the authenticated user (from JWT principal)
     * @return 200 OK with updated UserResponseDto where photoUrl is null
     */
    @DeleteMapping("/me/photo")
    public ResponseEntity<UserResponseDto> removePhoto(
            @AuthenticationPrincipal UserEntity currentUser) {
        UserResponseDto result = userService.removeProfilePhoto(currentUser.getId());
        return ResponseEntity.ok(result);
    }
}
