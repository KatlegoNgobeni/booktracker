package com.booktracker.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for POST /auth/register request body.
 * Jakarta Bean Validation constraints enforce D-03/D-06 input requirements.
 */
public class RegisterRequest {

    @Email(message = "must be a valid email address")
    @NotBlank(message = "must not be blank")
    private String email;

    @NotBlank(message = "must not be blank")
    @Size(min = 8, message = "must be at least 8 characters")
    private String password;

    @NotBlank(message = "must not be blank")
    private String displayName;

    public RegisterRequest() {}

    public RegisterRequest(String email, String password, String displayName) {
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
