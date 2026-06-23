package com.booktracker.user;

import java.time.OffsetDateTime;

/**
 * Read-only DTO for the GET /api/users/me response (D-10).
 *
 * <p>Contains only the fields the caller needs: id, email, displayName, createdAt.
 * The password hash is intentionally excluded — it must never appear in any API response.
 *
 * <p>Declared as a Java record for immutability and compact syntax.
 */
public record UserResponseDto(
        String id,
        String email,
        String displayName,
        OffsetDateTime createdAt
) {}
