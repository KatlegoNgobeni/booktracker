package com.booktracker.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest slice for UserController — validates GET /users/me response shape.
 * Security is excluded (tests focus on response mapping, not security enforcement).
 */
@WebMvcTest(
    controllers = UserController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void getMeSuccess_returnsUserProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        UserResponseDto dto = new UserResponseDto(
                userId.toString(), "me@example.com", "My Name", now);

        UserDetails principal = User.builder()
                .username(userId.toString())
                .password("ignored")
                .authorities(List.of())
                .build();

        when(userService.getUserById(userId)).thenReturn(dto);

        mockMvc.perform(get("/users/me")
                .with(user(principal)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value(userId.toString()))
               .andExpect(jsonPath("$.email").value("me@example.com"))
               .andExpect(jsonPath("$.displayName").value("My Name"))
               .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void getMeResponse_containsNoPasswordField() throws Exception {
        UUID userId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        UserResponseDto dto = new UserResponseDto(
                userId.toString(), "me@example.com", "My Name", now);

        UserDetails principal = User.builder()
                .username(userId.toString())
                .password("ignored")
                .authorities(List.of())
                .build();

        when(userService.getUserById(userId)).thenReturn(dto);

        mockMvc.perform(get("/users/me")
                .with(user(principal)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.password").doesNotExist())
               .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
