package com.booktracker.user;

import com.booktracker.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for UserController — validates GET /users/me response shape (AUTH-03, D-10).
 *
 * <p>Spring Security is kept active (no SecurityAutoConfiguration exclusion) so that
 * {@code @AuthenticationPrincipal} resolution works via the framework's argument resolver.
 * {@code UserDetailsServiceAutoConfiguration} is excluded to prevent it from creating a
 * default in-memory {@code UserDetailsService} bean that would conflict with our
 * {@code @MockitoBean UserService} (which implements {@code UserDetailsService}).
 *
 * <p>Uses {@code @WithMockUser} to inject a deterministic UUID username into the
 * SecurityContext without needing a real JWT or Testcontainers database.
 */
@WebMvcTest(
    controllers = UserController.class,
    excludeAutoConfiguration = {UserDetailsServiceAutoConfiguration.class}
)
class UserControllerTest {

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
     * AUTH-03 / D-10: GET /users/me returns the caller's profile (id, email, displayName, createdAt).
     * The @WithMockUser username is a valid UUID string so UUID.fromString succeeds in the controller.
     */
    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void getMeSuccess_returnsUserProfile() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        String userId = "00000000-0000-0000-0000-000000000001";
        UserResponseDto dto = new UserResponseDto(userId, "me@example.com", "My Name", now);

        when(userService.getUserById(any())).thenReturn(dto);

        mockMvc.perform(get("/api/users/me"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value(userId))
               .andExpect(jsonPath("$.email").value("me@example.com"))
               .andExpect(jsonPath("$.displayName").value("My Name"))
               .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    /**
     * D-10 security requirement: the /users/me response must never contain a password field.
     */
    @Test
    @WithMockUser(username = "00000000-0000-0000-0000-000000000001")
    void getMeResponse_containsNoPasswordField() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        String userId = "00000000-0000-0000-0000-000000000001";
        UserResponseDto dto = new UserResponseDto(userId, "me@example.com", "My Name", now);

        when(userService.getUserById(any())).thenReturn(dto);

        mockMvc.perform(get("/api/users/me"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.password").doesNotExist())
               .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
