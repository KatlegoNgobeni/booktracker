package com.booktracker.goal;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for GoalController — validates request/response shapes
 * for goal endpoints WITHOUT loading the full security context.
 *
 * <p>Security is excluded via {@code excludeAutoConfiguration} (same pattern as
 * {@code ShelfControllerTest}) because these tests focus on input validation
 * behaviour (Bean Validation → 400), not security enforcement.
 *
 * <p>GoalService is mocked with {@code @MockitoBean} (Spring Boot 3.4+ replacement
 * for deprecated {@code @MockBean} — CLAUDE.md §Version Gotchas).
 *
 * <p>STATS-01 acceptance criteria covered here:
 * <ul>
 *   <li>targetCount = -1 → 400 (T-05-04 — @Min(0) validation)</li>
 *   <li>targetCount = null → 400 (@NotNull validation)</li>
 * </ul>
 */
@WebMvcTest(
    controllers = GoalController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class GoalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Mock of GoalService — prevents the slice from trying to wire the real service.
     */
    @MockitoBean
    private GoalService goalService;

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
     * T-05-04: A negative targetCount must return 400 Bad Request (Bean Validation @Min(0)).
     */
    @Test
    void setGoal_negativeTargetCount_returns400() throws Exception {
        mockMvc.perform(put("/goal")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetCount\": -1}"))
               .andExpect(status().isBadRequest());
    }

    /**
     * T-05-04: A null targetCount must return 400 Bad Request (@NotNull validation).
     */
    @Test
    void setGoal_nullTargetCount_returns400() throws Exception {
        mockMvc.perform(put("/goal")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetCount\": null}"))
               .andExpect(status().isBadRequest());
    }
}
