package com.booktracker.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UserService — loadUserByUsername (UUID string → UserDetails)
 * and getUserById (UUID → UserResponseDto, D-10).
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private UserEntity sampleUser;
    private UUID sampleId;

    @BeforeEach
    void setUp() {
        sampleId = UUID.randomUUID();
        sampleUser = new UserEntity();
        sampleUser.setId(sampleId);
        sampleUser.setEmail("test@example.com");
        sampleUser.setPasswordHash("$2a$10$hash");
        sampleUser.setDisplayName("Test User");
        sampleUser.setCreatedAt(OffsetDateTime.now());
    }

    // ----------------------------------------------------------------
    // loadUserByUsername
    // ----------------------------------------------------------------

    @Test
    void loadUserByUsername_validUuid_returnsUserDetails() {
        when(userRepository.findById(sampleId)).thenReturn(Optional.of(sampleUser));

        var result = userService.loadUserByUsername(sampleId.toString());

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo(sampleId.toString());
    }

    @Test
    void loadUserByUsername_unknownUuid_throwsUsernameNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername(unknownId.toString()))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByUsername_invalidUuidString_throwsException() {
        assertThatThrownBy(() -> userService.loadUserByUsername("not-a-uuid"))
                .isInstanceOf(Exception.class);
    }

    // ----------------------------------------------------------------
    // getUserById
    // ----------------------------------------------------------------

    @Test
    void getUserById_existingUser_returnsDto() {
        when(userRepository.findById(sampleId)).thenReturn(Optional.of(sampleUser));

        UserResponseDto dto = userService.getUserById(sampleId);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(sampleId.toString());
        assertThat(dto.email()).isEqualTo("test@example.com");
        assertThat(dto.displayName()).isEqualTo("Test User");
        assertThat(dto.createdAt()).isNotNull();
    }

    @Test
    void getUserById_dtoContainsNoPasswordField() {
        when(userRepository.findById(sampleId)).thenReturn(Optional.of(sampleUser));

        UserResponseDto dto = userService.getUserById(sampleId);

        // UserResponseDto is a record — verify it has no password component
        // The record fields are id, email, displayName, createdAt
        assertThat(dto.getClass().getRecordComponents()).hasSize(4);
        for (var component : dto.getClass().getRecordComponents()) {
            assertThat(component.getName()).doesNotContainIgnoringCase("password");
            assertThat(component.getName()).doesNotContainIgnoringCase("hash");
        }
    }

    @Test
    void getUserById_unknownUser_throwsException() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(unknownId))
                .isInstanceOf(Exception.class);
    }
}
