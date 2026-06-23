package com.booktracker.health;

import com.booktracker.security.JwtUtil;
import com.booktracker.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = HealthController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * MockitoBean provides a Mockito mock of DbHealthIndicator into the
     * @WebMvcTest slice context. This replaces the Plan 01 no-op version
     * and avoids the need for a real DataSource in the slice.
     * Note: @MockitoBean replaces @MockBean in Spring Boot 3.4+.
     */
    @MockitoBean
    private DbHealthIndicator dbHealthIndicator;

    /**
     * Mock JwtUtil — required because JwtAuthenticationFilter is a @Component scanned
     * into the @WebMvcTest context even when SecurityAutoConfiguration is excluded.
     * (SecurityFilterAutoConfiguration exclusion prevents the filter chain from running,
     * but the bean must still be constructable.) Added in plan 02-02 when JwtAuthenticationFilter
     * was introduced.
     */
    @MockitoBean
    private JwtUtil jwtUtil;

    /**
     * Mock UserService — satisfies JwtAuthenticationFilter's UserDetailsService injection.
     * UserService implements UserDetailsService, so one mock covers both.
     */
    @MockitoBean
    private UserService userService;

    @Test
    void healthEndpointReturns200WithStatusOk() throws Exception {
        when(dbHealthIndicator.isDbReachable()).thenReturn(true);

        mockMvc.perform(get("/health"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void healthEndpointReturns503WhenDatabaseIsDown() throws Exception {
        when(dbHealthIndicator.isDbReachable()).thenReturn(false);

        mockMvc.perform(get("/health"))
               .andExpect(status().isServiceUnavailable())
               .andExpect(jsonPath("$.status").value("down"));
    }
}
