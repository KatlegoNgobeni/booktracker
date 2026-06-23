package com.booktracker.auth;

import java.time.OffsetDateTime;

/**
 * Response DTO for POST /auth/register and POST /auth/login (D-05).
 * Contains the JWT token and a user representation without the password.
 */
public class AuthResponse {

    private String token;
    private UserDto user;

    public AuthResponse() {}

    public AuthResponse(String token, UserDto user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public UserDto getUser() { return user; }
    public void setUser(UserDto user) { this.user = user; }

    /**
     * Nested user representation — matches D-05 shape:
     * {@code {"id": "...", "email": "...", "displayName": "...", "createdAt": "..."}}.
     * No password field.
     */
    public static class UserDto {
        private String id;
        private String email;
        private String displayName;
        private OffsetDateTime createdAt;

        public UserDto() {}

        public UserDto(String id, String email, String displayName, OffsetDateTime createdAt) {
            this.id = id;
            this.email = email;
            this.displayName = displayName;
            this.createdAt = createdAt;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public OffsetDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    }
}
