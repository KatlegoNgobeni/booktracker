package com.booktracker.auth;

import com.booktracker.security.JwtUtil;
import com.booktracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for AuthController — validates request/response shapes
 * for the POST /auth/register endpoint WITHOUT loading the full security context.
 *
 * <p>Security is excluded via {@code excludeAutoConfiguration} (same pattern as
 * {@code HealthControllerTest}) because these tests focus on input validation
 * behaviour (Bean Validation → 400), not security enforcement.
 *
 * <p>AuthService is mocked with {@code @MockitoBean} (Spring Boot 3.4+ replacement
 * for deprecated {@code @MockBean} — CLAUDE.md §Version Gotchas).
 *
 * <p>AUTH-01 acceptance criteria covered here:
 * <ul>
 *   <li>Malformed email → 400 + {"message": "Validation failed"}</li>
 *   <li>Password shorter than 8 chars → 400 + {"message": "Validation failed"}</li>
 * </ul>
 */
@WebMvcTest(
    controllers = AuthController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Mock of AuthService — prevents the slice from trying to wire the real service
     * (which depends on UserRepository, PasswordEncoder, JwtUtil — all unavailable
     * in this thin slice context).
     */
    @MockitoBean
    private AuthService authService;

    /**
     * Mock JwtUtil — required because JwtAuthenticationFilter is a @Component that gets
     * loaded into the @WebMvcTest context even when SecurityAutoConfiguration is excluded.
     * (SecurityFilterAutoConfiguration exclusion prevents the filter chain from running,
     * but the bean must still be constructable.)
     */
    @MockitoBean
    private JwtUtil jwtUtil;

    /**
     * Mock UserService — satisfies JwtAuthenticationFilter's UserDetailsService injection.
     * UserService implements UserDetailsService, so a single mock satisfies both constructor
     * parameters of JwtAuthenticationFilter.
     */
    @MockitoBean
    private UserService userService;

    /**
     * AUTH-01: An invalid email format must return 400 Bad Request with
     * a body containing {@code "message": "Validation failed"} (D-03).
     */
    @Test
    void registerInvalidEmail_returns400WithValidationFailed() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"password123\",\"displayName\":\"Test User\"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    /**
     * AUTH-01: A password shorter than 8 characters must return 400 Bad Request
     * with a body containing {@code "message": "Validation failed"} (D-03).
     */
    @Test
    void registerShortPassword_returns400WithValidationFailed() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"short\",\"displayName\":\"Test User\"}"))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.message").value("Validation failed"));
    }
}
