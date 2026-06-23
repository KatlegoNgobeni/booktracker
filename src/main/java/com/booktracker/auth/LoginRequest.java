package com.booktracker.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for POST /api/auth/login (AUTH-02).
 *
 * <p>Constraints:
 * <ul>
 *   <li>{@code @NotBlank email} — email field must be present and non-empty</li>
 *   <li>{@code @NotBlank password} — password field must be present and non-empty</li>
 * </ul>
 *
 * <p>Intentionally does NOT add {@code @Email} or {@code @Size(min=8)} on these fields:
 * <ul>
 *   <li>No {@code @Email} — login tries whatever the user typed; if it doesn't match an account
 *       the generic 401 is returned (D-03 — no field-level leakage)</li>
 *   <li>No {@code @Size(min=8)} on password — registration enforces minimum length, not login;
 *       adding length validation to login would leak information about valid account passwords</li>
 * </ul>
 */
public class LoginRequest {

    @NotBlank(message = "Email must not be blank")
    private String email;

    @NotBlank(message = "Password must not be blank")
    private String password;

    public LoginRequest() {}

    public LoginRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
