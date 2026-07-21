package com.booktracker.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

/**
 * Service for user profile reads, writes, and Spring Security's UserDetailsService contract.
 *
 * <p><strong>UserDetailsService contract (D-06):</strong> {@code loadUserByUsername(String)}
 * receives the UUID string from the JWT {@code sub} claim, parses it to a {@link UUID},
 * and loads the matching {@link UserEntity}. This is the primary UserDetailsService bean
 * used by both the {@code JwtAuthenticationFilter} and the {@code DaoAuthenticationProvider}.
 *
 * <p><strong>getUserById:</strong> Returns a {@link UserResponseDto} with
 * id, email, displayName, createdAt, photoUrl — no password hash (D-10, T-02-07).
 *
 * <p><strong>Photo endpoints (Phase 17):</strong> {@code uploadProfilePhoto} and
 * {@code removeProfilePhoto} are {@code @Transactional} write methods that update
 * {@code profile_photo_url} on the users table and return the updated DTO.
 */
@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       CloudinaryService cloudinaryService,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.cloudinaryService = cloudinaryService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Loads a user by their UUID string (the JWT {@code sub} claim).
     *
     * <p>Called by {@code JwtAuthenticationFilter} on every authenticated request.
     * The {@code username} parameter is the UUID string stored in the token's {@code sub} claim.
     *
     * @param uuid the user's UUID as a string (from {@code UserEntity.getUsername()})
     * @return the UserEntity as UserDetails
     * @throws UsernameNotFoundException if no user with that UUID exists
     * @throws IllegalArgumentException  if the string is not a valid UUID
     */
    @Override
    public UserDetails loadUserByUsername(String uuid) throws UsernameNotFoundException {
        UUID parsedId = UUID.fromString(uuid); // throws IllegalArgumentException if invalid
        return userRepository.findById(parsedId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + uuid));
    }

    /**
     * Returns the profile DTO for a user by UUID (D-10, AUTH-03).
     *
     * <p>Used by {@code UserController.me()} to serve GET /api/users/me.
     *
     * @param id the user's UUID
     * @return DTO with id, email, displayName, createdAt, photoUrl — never the password hash
     * @throws UsernameNotFoundException if no user with that ID exists
     */
    public UserResponseDto getUserById(UUID id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
        return new UserResponseDto(
                user.getId().toString(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCreatedAt(),
                user.getProfilePhotoUrl()
        );
    }

    /**
     * Uploads an image to Cloudinary and persists the resulting URL (Phase 17, PHOTO-01).
     *
     * <p>Validation (magic bytes + size) has already been performed by
     * {@link UserController} before this method is called. The {@code imageBytes}
     * parameter is the same buffer used for the Tika MIME check.
     *
     * <p><strong>T-17-04 (IDOR):</strong> The userId comes from
     * {@code @AuthenticationPrincipal} in the controller — never from a request parameter.
     *
     * <p><strong>T-17-06:</strong> Cloudinary credentials are injected into
     * {@link CloudinaryService} via the {@code Cloudinary} bean; they never appear
     * in the returned DTO.
     *
     * @param userId     the authenticated user's UUID
     * @param imageBytes validated image bytes (MIME + size already checked)
     * @return updated UserResponseDto with photoUrl set to the Cloudinary secure_url
     * @throws ResponseStatusException 502 Bad Gateway if the Cloudinary upload fails
     */
    @Transactional
    public UserResponseDto uploadProfilePhoto(UUID userId, byte[] imageBytes) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        String secureUrl;
        try {
            secureUrl = cloudinaryService.uploadProfilePhoto(imageBytes, userId);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Photo upload failed");
        }
        user.setProfilePhotoUrl(secureUrl);
        userRepository.save(user);
        return new UserResponseDto(
                user.getId().toString(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCreatedAt(),
                secureUrl
        );
    }

    /**
     * Removes the user's profile photo URL from the DB (Phase 17, PHOTO-04).
     *
     * <p>Sets {@code profile_photo_url = null} in the DB so the client reverts
     * to the Dicebear-generated avatar fallback (D-11). The actual Cloudinary asset
     * is not deleted — the deterministic {@code public_id} (profile_photos/{userId})
     * is simply overwritten on the next upload (overwrite=true strategy).
     *
     * <p><strong>T-17-04 (IDOR):</strong> The userId comes from
     * {@code @AuthenticationPrincipal} — never from a request parameter.
     *
     * @param userId the authenticated user's UUID
     * @return updated UserResponseDto with photoUrl null
     */
    @Transactional
    public UserResponseDto removeProfilePhoto(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        user.setProfilePhotoUrl(null);
        userRepository.save(user);
        return new UserResponseDto(
                user.getId().toString(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCreatedAt(),
                null
        );
    }

    /**
     * Partially updates the authenticated user's display name and/or password.
     *
     * <p>Rules:
     * <ul>
     *   <li>If {@code displayName} is non-null and non-blank, it is applied.</li>
     *   <li>If {@code newPassword} is non-null, {@code currentPassword} must match the stored
     *       BCrypt hash — returns 400 Bad Request if it doesn't (T-02-08).</li>
     *   <li>At least one of {@code displayName} or {@code newPassword} must be provided.</li>
     * </ul>
     *
     * @param userId  the authenticated user's UUID (from {@code @AuthenticationPrincipal})
     * @param request the partial-update request
     * @return updated UserResponseDto
     */
    @Transactional
    public UserResponseDto updateUser(UUID userId, UpdateUserRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));

        boolean changed = false;

        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            user.setDisplayName(request.getDisplayName().trim());
            changed = true;
        }

        if (request.getNewPassword() != null) {
            if (request.getCurrentPassword() == null
                    || !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
            }
            user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            changed = true;
        }

        if (!changed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No fields to update");
        }

        userRepository.save(user);
        return new UserResponseDto(
                user.getId().toString(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCreatedAt(),
                user.getProfilePhotoUrl()
        );
    }
}
