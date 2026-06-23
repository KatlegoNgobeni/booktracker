package com.booktracker.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JwtUtil — verifies token generation, subject round-trip,
 * expiry detection, and rejection of tokens signed with a different key.
 *
 * <p>The test secret {@code dGVzdC1zZWNyZXQtYmFzZTY0LWVuY29kZWQtMzJieXRlcw==}
 * is the base64 encoding of {@code test-secret-base64-encoded-32bytes} (33 bytes
 * when decoded — satisfies the ≥32-byte HMAC-SHA key requirement).
 *
 * <p>This test is a plain JUnit 5 unit test — no Spring context needed.
 * JwtUtil is constructed directly and the secret injected via the
 * package-private {@code setJwtSecret(String)} setter (avoids reflection).
 */
class JwtUtilTest {

    /**
     * Base64-encoded test secret that decodes to ≥32 bytes.
     * Decodes to: "test-secret-base64-encoded-32bytes" (34 bytes)
     */
    private static final String TEST_SECRET = "dGVzdC1zZWNyZXQtYmFzZTY0LWVuY29kZWQtMzJieXRlcw==";

    /**
     * Different secret for verifying key-mismatch rejection.
     * Decodes to: "different-secret-base64-32bytes-x" (33 bytes)
     */
    private static final String DIFFERENT_SECRET = "ZGlmZmVyZW50LXNlY3JldC1iYXNlNjQtMzJieXRlcy14";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // Package-private setter — avoids Spring context + reflection.
        // Only available in test scope (same package).
        jwtUtil.setJwtSecret(TEST_SECRET);
    }

    /**
     * AUTH-01 / AUTH-04: A token generated for a known UUID string
     * must parse back to the same subject.
     */
    @Test
    void generateToken_roundTripsSubject() {
        String userId = "550e8400-e29b-41d4-a716-446655440000";

        String token = jwtUtil.generateToken(userId);

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.extractSubject(token)).isEqualTo(userId);
    }

    /**
     * AUTH-04: A freshly generated token must not be reported as expired.
     */
    @Test
    void generateToken_isNotExpiredImmediately() {
        String token = jwtUtil.generateToken("some-uuid");

        assertThat(jwtUtil.isTokenExpired(token)).isFalse();
    }

    /**
     * AUTH-04: A token signed with a different key must be rejected
     * (JwtException or subtype) when parsed by a JwtUtil configured
     * with the original key. The verifier MUST throw — it must not
     * silently return a subject from a tampered token.
     */
    @Test
    void extractSubject_rejectsTokenSignedWithDifferentKey() {
        JwtUtil otherJwtUtil = new JwtUtil();
        otherJwtUtil.setJwtSecret(DIFFERENT_SECRET);

        // Token signed with DIFFERENT_SECRET
        String foreignToken = otherJwtUtil.generateToken("attacker-uuid");

        // JwtUtil configured with TEST_SECRET must reject it
        assertThatThrownBy(() -> jwtUtil.extractSubject(foreignToken))
                .isInstanceOf(JwtException.class);
    }
}
