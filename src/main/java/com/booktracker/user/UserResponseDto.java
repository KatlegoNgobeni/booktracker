package com.booktracker.user;

import java.time.OffsetDateTime;

/**
 * Read-only DTO for user profile responses (D-10).
 *
 * <p>Contains only the fields the caller needs: id, email, displayName, createdAt, photoUrl.
 * The password hash is intentionally excluded — it must never appear in any API response.
 *
 * <p>{@code photoUrl} is nullable — null when no profile photo has been uploaded.
 * The client renders a Dicebear-generated avatar fallback when photoUrl is null (D-11).
 * Only server-written Cloudinary {@code secure_url} values are ever stored here;
 * no user-supplied URLs are accepted (T-17-05 Stored XSS mitigation).
 *
 * <p>Declared as a Java record for immutability and compact syntax.
 */
public record UserResponseDto(
        String id,
        String email,
        String displayName,
        OffsetDateTime createdAt,
        String photoUrl          // nullable — null when no photo set (D-11)
) {}
