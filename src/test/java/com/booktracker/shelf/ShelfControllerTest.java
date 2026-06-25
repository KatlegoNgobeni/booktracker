package com.booktracker.shelf;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for ShelfController — validates request/response shapes
 * for shelf endpoints WITHOUT loading the full security context.
 *
 * <p>Security is excluded via {@code excludeAutoConfiguration} (same pattern as
 * {@code AuthControllerTest}) because these tests focus on input validation
 * behaviour (Bean Validation → 400), not security enforcement.
 *
 * <p>ShelfService is mocked with {@code @MockitoBean} (Spring Boot 3.4+ replacement
 * for deprecated {@code @MockBean} — CLAUDE.md §Version Gotchas).
 *
 * <p>SHELF-01 / T-04-03 / T-04-05 acceptance criteria covered here:
 * <ul>
 *   <li>Blank olKey → 400 (T-04-05 — @NotBlank validation)</li>
 *   <li>Unknown status string → 400 (T-04-03 — enum injection guard)</li>
 * </ul>
 */
@WebMvcTest(
    controllers = ShelfController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class ShelfControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Mock of ShelfService — prevents the slice from trying to wire the real service.
     */
    @MockitoBean
    private ShelfService shelfService;

    /**
     * Mock JwtUtil — required because JwtAuthenticationFilter is a @Component that gets
     * loaded into the @WebMvcTest context even when SecurityAutoConfiguration is excluded.
     */
    @MockitoBean
    private JwtUtil jwtUtil;

    /**
     * Mock UserService — satisfies JwtAuthenticationFilter's UserDetailsService injection.
     */
    @MockitoBean
    private UserService userService;

    /**
     * T-04-05: A blank olKey must return 400 Bad Request (Bean Validation @NotBlank).
     */
    @Test
    void addToShelf_blankOlKey_returns400() throws Exception {
        mockMvc.perform(post("/shelf")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"olKey\":\"\",\"status\":\"WANT_TO_READ\"}"))
               .andExpect(status().isBadRequest());
    }

    /**
     * T-04-03: An unknown status string (enum injection attempt) must return 400.
     * Jackson returns 400 automatically on unknown enum value — no custom handling needed.
     */
    @Test
    void addToShelf_unknownStatus_returns400() throws Exception {
        mockMvc.perform(post("/shelf")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"olKey\":\"OL45804W\",\"status\":\"READING\"}"))
               .andExpect(status().isBadRequest());
    }
}
