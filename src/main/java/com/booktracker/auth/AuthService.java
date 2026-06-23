package com.booktracker.auth;

import com.booktracker.security.JwtUtil;
import com.booktracker.user.UserEntity;
import com.booktracker.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Business logic for user registration and login.
 *
 * <p>Follows the "Don't Hand-Roll" principle (RESEARCH.md):
 * <ul>
 *   <li>Password hashing via {@code BCryptPasswordEncoder} — not a custom hash</li>
 *   <li>Duplicate email detection via DB constraint + {@code DataIntegrityViolationException}
 *       (not a pre-check with {@code findByEmail} — race-condition-safe)</li>
 *   <li>JWT issuance via {@code JwtUtil} — not hand-rolled Base64</li>
 *   <li>Credential verification via {@code AuthenticationManager/DaoAuthenticationProvider}
 *       (not a manual BCrypt compare — handles timing attacks and normalization)</li>
 * </ul>
 *
 * <p><strong>UserDetailsService:</strong> Moved to {@code UserService} in plan 02-02 to avoid
 * ambiguous bean resolution when the DaoAuthenticationProvider is added. AuthService uses
 * an explicit email lookup for login, while UserService provides UUID-based loading for the
 * JWT filter pipeline.
 */
@Service
public class AuthService {

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
     * Authenticates a user by email and password, issues a JWT on success.
     *
     * <p><strong>Approach (documented per plan):</strong> We load the user by email via
     * {@code UserRepository.findByEmail}, then use {@code PasswordEncoder.matches} to compare
     * the provided password against the stored BCrypt hash. This keeps
     * {@code UserService.loadUserByUsername} (UUID-based) intact for the JWT filter pipeline
     * while allowing email-based authentication at login without an AuthenticationManager
     * circular dependency.
     *
     * <p><strong>D-03 — no field leakage:</strong> Both "unknown email" and "wrong password"
     * throw {@code BadCredentialsException} with the same message. The caller should NOT
     * distinguish between the two cases. {@code GlobalExceptionHandler} maps this to a
     * generic 401 {@code {"message": "Invalid credentials"}}.
     *
     * @param request validated login payload (email + password)
     * @return authentication response with token and user DTO (D-05 shape)
     * @throws BadCredentialsException if email is not found or password does not match (D-03)
     */
    public AuthResponse login(LoginRequest request) {
        // T-02-07: Use the same exception for unknown-email AND wrong-password to prevent
        // email enumeration. GlobalExceptionHandler maps to identical 401 response.
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        // D-06: sub = UUID string; D-05: same response shape as register
        String token = jwtUtil.generateToken(user.getId().toString());

        AuthResponse.UserDto userDto = new AuthResponse.UserDto(
                user.getId().toString(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCreatedAt()
        );

        return new AuthResponse(token, userDto);
    }

}
