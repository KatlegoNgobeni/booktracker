package com.booktracker.auth;

import com.booktracker.security.JwtUtil;
import com.booktracker.user.UserEntity;
import com.booktracker.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Business logic for user registration.
 *
 * <p>Follows the "Don't Hand-Roll" principle (RESEARCH.md):
 * <ul>
 *   <li>Password hashing via {@code BCryptPasswordEncoder} — not a custom hash</li>
 *   <li>Duplicate email detection via DB constraint + {@code DataIntegrityViolationException}
 *       (not a pre-check with {@code findByEmail} — race-condition-safe)</li>
 *   <li>JWT issuance via {@code JwtUtil} — not hand-rolled Base64</li>
 * </ul>
 *
 * <p>Implements {@code UserDetailsService} so Spring Security's
 * {@code DaoAuthenticationProvider} (added in plan 02) can load users by UUID string.
 */
@Service
public class AuthService implements org.springframework.security.core.userdetails.UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Registers a new user: hashes the password, persists the entity, and issues a JWT.
     *
     * <p>Duplicate email raises {@code DataIntegrityViolationException} from the DB unique
     * constraint — caught by {@code GlobalExceptionHandler} and returned as 409 (D-04).
     *
     * @param request validated registration payload
     * @return authentication response with token and user DTO (D-05)
     */
    public AuthResponse register(RegisterRequest request) {
        UserEntity user = new UserEntity();
        user.setEmail(request.getEmail());
        // T-02-02: BCrypt hash — never store plaintext (threat model)
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());

        // Let the DB unique constraint raise DataIntegrityViolationException on duplicate email.
        // This is race-condition-safe (no TOCTOU window from a pre-check findByEmail call).
        UserEntity saved = userRepository.save(user);

        // D-06: sub = UUID string; D-05: auto-login — return token immediately
        String token = jwtUtil.generateToken(saved.getId().toString());

        AuthResponse.UserDto userDto = new AuthResponse.UserDto(
            saved.getId().toString(),
            saved.getEmail(),
            saved.getDisplayName(),
            saved.getCreatedAt()
        );

        return new AuthResponse(token, userDto);
    }

    /**
     * Loads a user by their UUID string (the JWT {@code sub} claim).
     * Used by Spring Security's filter pipeline (plan 02) — not by plan 01 register flow.
     *
     * @param uuid the UUID string from the JWT sub claim
     * @return the UserEntity as UserDetails
     * @throws org.springframework.security.core.userdetails.UsernameNotFoundException
     *         if no user with that UUID exists
     */
    @Override
    public org.springframework.security.core.userdetails.UserDetails loadUserByUsername(String uuid)
            throws org.springframework.security.core.userdetails.UsernameNotFoundException {
        return userRepository.findById(java.util.UUID.fromString(uuid))
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(
                        "User not found: " + uuid));
    }
}
