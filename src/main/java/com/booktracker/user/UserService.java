package com.booktracker.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for user profile reads and Spring Security's UserDetailsService contract.
 *
 * <p><strong>UserDetailsService contract (D-06):</strong> {@code loadUserByUsername(String)}
 * receives the UUID string from the JWT {@code sub} claim, parses it to a {@link UUID},
 * and loads the matching {@link UserEntity}. This is the primary UserDetailsService bean
 * used by both the {@code JwtAuthenticationFilter} and the {@code DaoAuthenticationProvider}.
 *
 * <p><strong>getUserById:</strong> Returns a {@link UserResponseDto} with
 * id, email, displayName, createdAt — no password hash (D-10, T-02-07).
 */
@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
     * @return DTO with id, email, displayName, createdAt — never the password hash
     * @throws UsernameNotFoundException if no user with that ID exists
     */
    public UserResponseDto getUserById(UUID id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + id));
        return new UserResponseDto(
                user.getId().toString(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCreatedAt()
        );
    }
}
