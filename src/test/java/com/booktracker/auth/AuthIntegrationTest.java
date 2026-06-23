package com.booktracker.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for POST /auth/register using a real PostgreSQL 16
 * container (Testcontainers) and a full Spring context with a random HTTP port.
 *
 * <p>Follows the Testcontainers + @DynamicPropertySource pattern established in
 * {@code SchemaMigrationTest}. Adds {@code webEnvironment = RANDOM_PORT} so
 * {@code TestRestTemplate} can make real HTTP calls to the running server.
 *
 * <p>A test JWT secret is registered via {@code @DynamicPropertySource} so the
 * full Spring context (including JwtUtil) starts successfully.
 *
 * <p>AUTH-01 acceptance criteria covered here:
 * <ul>
 *   <li>Valid registration → 201 + non-empty token + user object (id, email, displayName, createdAt)</li>
 *   <li>Duplicate email → 409 Conflict (D-04)</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthIntegrationTest {

    /** Shared PostgreSQL 16 container — started once for the class. */
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("booktracker_test")
                    .withUsername("bt_test")
                    .withPassword("bt_test_pw");

    /**
     * Wires the Testcontainers JDBC URL/credentials and a test JWT secret into
     * the Spring datasource properties so Flyway, Hibernate, and JwtUtil all
     * start correctly.
     *
     * <p>The test JWT secret {@code dGVzdC1zZWNyZXQtYmFzZTY0LWVuY29kZWQtMzJieXRlcw==}
     * is base64("test-secret-base64-encoded-32bytes") — 34 decoded bytes, satisfying
     * the ≥32-byte HMAC-SHA key requirement (D-07).
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled",      () -> "true");
        // Test JWT secret — base64-encoded, decodes to 34 bytes (D-07: ≥32 bytes)
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQtYmFzZTY0LWVuY29kZWQtMzJieXRlcw==");
    }

    /**
     * TestRestTemplate makes real HTTP calls to the embedded server.
     * Automatically resolves the base URL to http://localhost:{randomPort}.
     */
    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * AUTH-01: A valid registration must return 201 Created with:
     * - a non-empty JWT token
     * - a user object containing id, email, displayName, createdAt
     * - no password field in the response (D-05)
     */
    @Test
    @SuppressWarnings("unchecked")
    void registerSuccess_returns201WithTokenAndUser() {
        Map<String, Object> body = Map.of(
            "email",       "alice@example.com",
            "password",    "securepassword123",
            "displayName", "Alice"
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/auth/register", body, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<?, ?> responseBody = response.getBody();
        assertThat(responseBody).isNotNull();

        // Token must be present and non-empty
        assertThat(responseBody.get("token")).isNotNull();
        assertThat(responseBody.get("token").toString()).isNotBlank();

        // User object must be present with required fields
        Object userObj = responseBody.get("user");
        assertThat(userObj).isNotNull();
        Map<?, ?> user = (Map<?, ?>) userObj;
        assertThat(user.get("id")).isNotNull();
        assertThat(user.get("email")).isEqualTo("alice@example.com");
        assertThat(user.get("displayName")).isEqualTo("Alice");
        assertThat(user.get("createdAt")).isNotNull();

        // No password field in response (security requirement)
        assertThat(user.containsKey("password")).isFalse();
        assertThat(user.containsKey("passwordHash")).isFalse();
    }

    /**
     * AUTH-01 + D-04: Registering the same email twice must return 409 Conflict
     * with a generic message that does not reveal the conflicting field beyond
     * the email itself.
     */
    @Test
    @SuppressWarnings("unchecked")
    void registerDuplicateEmail_returns409() {
        Map<String, Object> body = Map.of(
            "email",       "bob@example.com",
            "password",    "securepassword123",
            "displayName", "Bob"
        );

        // First registration succeeds
        ResponseEntity<Map> first = restTemplate.postForEntity(
            "/auth/register", body, Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second registration with the same email must return 409
        ResponseEntity<Map> second = restTemplate.postForEntity(
            "/auth/register", body, Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        Map<?, ?> errorBody = second.getBody();
        assertThat(errorBody).isNotNull();
        assertThat(errorBody.get("message")).isEqualTo("Email already registered");
    }
}
