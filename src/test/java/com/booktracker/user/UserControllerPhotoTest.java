package com.booktracker.user;

import com.booktracker.security.JwtUtil;
import com.booktracker.social.FriendRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for UserController photo endpoints — covers PHOTO-01, PHOTO-03, PHOTO-04.
 *
 * <p>Tests the four core behaviors of the photo upload and removal endpoints:
 * <ol>
 *   <li>Valid JPEG upload returns 200 with a UserResponseDto containing a Cloudinary photoUrl</li>
 *   <li>File over 5 MB returns 400 Bad Request (T-17-02 / D-05)</li>
 *   <li>Non-image file (ZIP magic bytes) returns 415 Unsupported Media Type (T-17-01 / PHOTO-03)</li>
 *   <li>DELETE /me/photo returns 200 with photoUrl null (PHOTO-04)</li>
 * </ol>
 *
 * <p>Uses {@code @MockitoBean} (Spring Boot 3.4+ convention — NOT {@code @MockBean}) for
 * {@link UserService}, {@link CloudinaryService}, and {@link FriendRequestService}.
 * {@code CloudinaryService} is mocked to prevent real Cloudinary HTTP calls in tests.
 *
 * <p>Spring Security is kept active (no SecurityAutoConfiguration exclusion) so that
 * {@code @AuthenticationPrincipal} resolution works via the framework's argument resolver.
 * {@code UserDetailsServiceAutoConfiguration} is excluded to prevent it from creating a
 * default in-memory {@code UserDetailsService} bean that would conflict with
 * {@code @MockitoBean UserService} (which implements {@code UserDetailsService}).
 *
 * <p>The photo endpoints use {@code @AuthenticationPrincipal UserEntity} — unlike
 * {@code @WithMockUser} which provides a generic Spring Security {@code User}, we use
 * {@code SecurityMockMvcRequestPostProcessors.user(UserEntity)} to inject a real
 * {@code UserEntity} instance as the authenticated principal. This avoids the 403 that
 * results from Spring's type-mismatch when casting a generic {@code User} to {@code UserEntity}.
 */
@WebMvcTest(
    controllers = UserController.class,
    excludeAutoConfiguration = {UserDetailsServiceAutoConfiguration.class}
)
class UserControllerPhotoTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Mock UserService — satisfies UserController AND JwtAuthenticationFilter's
     * UserDetailsService injection (single bean, no ambiguity once the default
     * in-memory UserDetailsService auto-config is excluded).
     */
    @MockitoBean
    private UserService userService;

    /** Required by JwtAuthenticationFilter, which is auto-scanned into the slice. */
    @MockitoBean
    private JwtUtil jwtUtil;

    /**
     * CloudinaryService mock — prevents real Cloudinary HTTP calls in tests.
     * Not directly invoked in these tests (the controller delegates to userService),
     * but must be present in the application context because UserService depends
     * on it via constructor injection.
     */
    @MockitoBean
    private CloudinaryService cloudinaryService;

    /** Required by UserController's constructor (friend-status enrichment on search). */
    @MockitoBean
    private FriendRequestService friendRequestService;

    /** Reusable UserEntity principal for SecurityMockMvcRequestPostProcessors.user(). */
    private UserEntity testUser;

    private static final String TEST_USER_ID = "00000000-0000-0000-0000-000000000001";

    @BeforeEach
    void setUp() {
        testUser = new UserEntity();
        testUser.setId(UUID.fromString(TEST_USER_ID));
        testUser.setEmail("user@example.com");
        testUser.setDisplayName("Test User");
        testUser.setCreatedAt(OffsetDateTime.now());
        testUser.setPasswordHash("$2a$10$hash");
    }

    /**
     * PHOTO-01: Valid JPEG upload (magic bytes 0xFF 0xD8 0xFF) returns 200 OK with
     * a UserResponseDto containing a non-null Cloudinary photoUrl.
     *
     * <p>JPEG magic bytes ensure Apache Tika detects {@code image/jpeg} — the
     * content type attribute on MockMultipartFile is secondary; Tika reads bytes.
     */
    @Test
    void uploadPhoto_validJpeg_returns200() throws Exception {
        String cloudinaryUrl = "https://res.cloudinary.com/test/image/upload/profile_photos/test.jpg";
        UserResponseDto dto = new UserResponseDto(
                TEST_USER_ID,
                "user@example.com",
                "Test User",
                OffsetDateTime.now(),
                cloudinaryUrl
        );
        when(userService.uploadProfilePhoto(any(UUID.class), any(byte[].class))).thenReturn(dto);

        // JPEG magic bytes: 0xFF 0xD8 0xFF (first 3 bytes identify JPEG)
        byte[] jpegMagicBytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                                  0x00, 0x10, 0x4A, 0x46, 0x49, 0x46};
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", jpegMagicBytes);

        mockMvc.perform(multipart("/api/users/me/photo")
                        .file(file)
                        .with(user(testUser))
                        .with(csrf()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.photoUrl").value(cloudinaryUrl));
    }

    /**
     * PHOTO-03 / T-17-02: File exceeding 5 MB returns 400 Bad Request.
     *
     * <p>The size gate runs BEFORE the Tika magic-byte check, so even a valid
     * image content type is rejected if the file is too large.
     */
    @Test
    void uploadPhoto_tooLarge_returns400() throws Exception {
        // Create a byte array slightly over 5 MB
        byte[] largeBytes = new byte[5 * 1024 * 1024 + 1];
        // Fill leading bytes with JPEG magic so it would pass MIME check if size check is skipped
        largeBytes[0] = (byte) 0xFF;
        largeBytes[1] = (byte) 0xD8;
        largeBytes[2] = (byte) 0xFF;

        MockMultipartFile file = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", largeBytes);

        mockMvc.perform(multipart("/api/users/me/photo")
                        .file(file)
                        .with(user(testUser))
                        .with(csrf()))
               .andExpect(status().isBadRequest());
    }

    /**
     * PHOTO-03 / T-17-01: Non-image file (ZIP magic bytes 0x50 0x4B 0x03 0x04)
     * returns 415 Unsupported Media Type.
     *
     * <p>Apache Tika detects the true MIME type from bytes — the {@code content-type}
     * attribute {@code "application/zip"} on the MockMultipartFile is irrelevant to
     * the server-side check (client Content-Type header is spoofable; T-17-01 mitigation).
     */
    @Test
    void uploadPhoto_nonImage_returns415() throws Exception {
        // ZIP magic bytes: 0x50 0x4B 0x03 0x04
        byte[] zipMagicBytes = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file", "malicious.zip", "application/zip", zipMagicBytes);

        mockMvc.perform(multipart("/api/users/me/photo")
                        .file(file)
                        .with(user(testUser))
                        .with(csrf()))
               .andExpect(status().isUnsupportedMediaType());
    }

    /**
     * PHOTO-04: DELETE /me/photo returns 200 OK with a UserResponseDto where photoUrl is null.
     *
     * <p>Verifies the remove-photo endpoint contract: the client receives the updated
     * DTO with photoUrl null so it can immediately revert the avatar to Dicebear (D-11).
     */
    @Test
    void removePhoto_returns200() throws Exception {
        UserResponseDto dto = new UserResponseDto(
                TEST_USER_ID,
                "user@example.com",
                "Test User",
                OffsetDateTime.now(),
                null   // photoUrl null — client reverts to Dicebear (D-11)
        );
        when(userService.removeProfilePhoto(any(UUID.class))).thenReturn(dto);

        mockMvc.perform(delete("/api/users/me/photo")
                        .with(user(testUser))
                        .with(csrf()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.photoUrl").doesNotExist());
    }
}
