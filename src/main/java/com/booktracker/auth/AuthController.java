package com.booktracker.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication endpoints.
 *
 * <p>Mapped under {@code /auth} (becomes {@code /api/auth} via context-path).
 * Security permits {@code /auth/register} unauthenticated (D-08).
 *
 * <p>Follows the constructor-injection pattern from {@code HealthController}.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/register — create account and issue JWT (AUTH-01, D-05).
     *
     * @param request validated registration payload ({@code @Valid} triggers Bean Validation)
     * @return 201 Created with token and user DTO; 400 on validation failure; 409 on duplicate email
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/auth/login — authenticate with email and password, receive JWT (AUTH-02, D-05).
     *
     * <p>Returns 200 OK with the same {@link AuthResponse} shape as register (token + user).
     * On invalid credentials, {@code BadCredentialsException} propagates to
     * {@code GlobalExceptionHandler} which returns 401 {@code {"message": "Invalid credentials"}}
     * — identical message for wrong-password and unknown-email (D-03, T-02-07).
     *
     * @param request validated login payload ({@code @Valid} triggers @NotBlank constraints)
     * @return 200 OK with token and user DTO; 401 on bad credentials; 400 on blank fields
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
