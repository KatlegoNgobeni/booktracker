package com.booktracker.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
 * Integration tests for the authentication endpoints using a real PostgreSQL 16
 * container (Testcontainers) and a full Spring context with a random HTTP port.
 *
 * <p>Follows the Testcontainers + @DynamicPropertySource pattern established in
 * {@code SchemaMigrationTest}. Uses {@code TestRestTemplate} with RANDOM_PORT for real HTTP calls.
 *
 * <p>A test JWT secret is registered via @DynamicPropertySource — same value as in 02-01.
 *
 * <p>AUTH-01..04 acceptance criteria covered:
 * <ul>
 *   <li>AUTH-01: Valid registration → 201 + token + user (existing)</li>
 *   <li>AUTH-01: Duplicate email → 409 (existing)</li>
 *   <li>AUTH-02: Login with valid credentials → 200 + token</li>
 *   <li>AUTH-02: Login with wrong password → 401 with generic message</li>
 *   <li>AUTH-02: Login with unknown email → 401 with IDENTICAL message (D-03)</li>
 *   <li>AUTH-03: GET /users/me with valid JWT → 200 + user profile</li>
 *   <li>AUTH-03/04: GET /users/me with no token → 401</li>
 *   <li>AUTH-04: GET /users/me with malformed token → 401</li>
 *   <li>AUTH-04: STATELESS — no JSESSIONID on authenticated response</li>
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
     * Wires Testcontainers JDBC URL/credentials and a test JWT secret into the Spring
     * datasource properties so Flyway, Hibernate, and JwtUtil all start correctly.
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
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQtYmFzZTY0LWVuY29kZWQtMzJieXRlcw==");
    }

    /**
     * TestRestTemplate makes real HTTP calls to the embedded server.
     * Base URL is automatically set to http://localhost:{randomPort}/api
     * (includes context-path) — test URLs must omit the /api prefix.
     */
    @Autowired
    private TestRestTemplate restTemplate;

    // ----------------------------------------------------------------
    // AUTH-01: Register (existing tests preserved)
    // ----------------------------------------------------------------

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

        assertThat(responseBody.get("token")).isNotNull();
        assertThat(responseBody.get("token").toString()).isNotBlank();

        Object userObj = responseBody.get("user");
        assertThat(userObj).isNotNull();
        Map<?, ?> user = (Map<?, ?>) userObj;
        assertThat(user.get("id")).isNotNull();
        assertThat(user.get("email")).isEqualTo("alice@example.com");
        assertThat(user.get("displayName")).isEqualTo("Alice");
        assertThat(user.get("createdAt")).isNotNull();

        assertThat(user.containsKey("password")).isFalse();
        assertThat(user.containsKey("passwordHash")).isFalse();
    }

    /**
     * AUTH-01 + D-04: Registering the same email twice must return 409 Conflict.
     */
    @Test
    @SuppressWarnings("unchecked")
    void registerDuplicateEmail_returns409() {
        Map<String, Object> body = Map.of(
            "email",       "bob@example.com",
            "password",    "securepassword123",
            "displayName", "Bob"
        );

        ResponseEntity<Map> first = restTemplate.postForEntity(
            "/auth/register", body, Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> second = restTemplate.postForEntity(
            "/auth/register", body, Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        Map<?, ?> errorBody = second.getBody();
        assertThat(errorBody).isNotNull();
        assertThat(errorBody.get("message")).isEqualTo("Email already registered");
    }

    // ----------------------------------------------------------------
    // AUTH-02: Login
    // ----------------------------------------------------------------

    /**
     * AUTH-02: Valid login returns 200 with token and user (same shape as register — D-05).
     */
    @Test
    @SuppressWarnings("unchecked")
    void loginSuccess_returns200WithTokenAndUser() {
        // Register first
        Map<String, Object> registerBody = Map.of(
            "email",       "charlie@example.com",
            "password",    "securepassword123",
            "displayName", "Charlie"
        );
        restTemplate.postForEntity("/auth/register", registerBody, Map.class);

        // Login
        Map<String, Object> loginBody = Map.of(
            "email",    "charlie@example.com",
            "password", "securepassword123"
        );
        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/auth/login", loginBody, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> responseBody = response.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("token")).isNotNull();
        assertThat(responseBody.get("token").toString()).isNotBlank();

        Object userObj = responseBody.get("user");
        assertThat(userObj).isNotNull();
        Map<?, ?> user = (Map<?, ?>) userObj;
        assertThat(user.get("email")).isEqualTo("charlie@example.com");
        assertThat(user.get("displayName")).isEqualTo("Charlie");
        assertThat(user.containsKey("password")).isFalse();
        assertThat(user.containsKey("passwordHash")).isFalse();
    }

    /**
     * AUTH-02 + D-03: Wrong password returns 401 with generic message (no field leakage).
     */
    @Test
    @SuppressWarnings("unchecked")
    void loginWrongPassword_returns401() {
        // Register
        Map<String, Object> registerBody = Map.of(
            "email",       "diana@example.com",
            "password",    "correctpassword123",
            "displayName", "Diana"
        );
        restTemplate.postForEntity("/auth/register", registerBody, Map.class);

        // Login with wrong password
        Map<String, Object> loginBody = Map.of(
            "email",    "diana@example.com",
            "password", "wrongpassword123"
        );
        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/auth/login", loginBody, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("message")).isEqualTo("Invalid credentials");
    }

    /**
     * AUTH-02 + D-03: Unknown email returns 401 with the IDENTICAL message as wrong password.
     * This prevents email enumeration (T-02-07).
     */
    @Test
    @SuppressWarnings("unchecked")
    void loginUnknownEmail_returns401WithIdenticalMessage() {
        // Login with non-existent email
        Map<String, Object> loginBody = Map.of(
            "email",    "nobody@example.com",
            "password", "anypassword123"
        );
        ResponseEntity<Map> response = restTemplate.postForEntity(
            "/auth/login", loginBody, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();

        // D-03 / T-02-07: MUST be byte-for-byte identical to the wrong-password message
        assertThat(body.get("message")).isEqualTo("Invalid credentials");
    }

    // ----------------------------------------------------------------
    // AUTH-03: GET /users/me
    // ----------------------------------------------------------------

    /**
     * AUTH-03: GET /api/users/me with valid Bearer token returns 200 + user profile (D-10).
     */
    @Test
    @SuppressWarnings("unchecked")
    void getMeSuccess_returnsOwnProfile() {
        // Register and capture token
        Map<String, Object> registerBody = Map.of(
            "email",       "eve@example.com",
            "password",    "securepassword123",
            "displayName", "Eve"
        );
        ResponseEntity<Map> registerResponse = restTemplate.postForEntity(
            "/auth/register", registerBody, Map.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String token = registerResponse.getBody().get("token").toString();

        // GET /users/me with Bearer token
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
            "/users/me", HttpMethod.GET, request, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("email")).isEqualTo("eve@example.com");
        assertThat(body.get("displayName")).isEqualTo("Eve");
        assertThat(body.get("id")).isNotNull();
        assertThat(body.get("createdAt")).isNotNull();
        assertThat(body.containsKey("password")).isFalse();
        assertThat(body.containsKey("passwordHash")).isFalse();
    }

    /**
     * AUTH-04: GET /api/users/me with no Authorization header returns 401.
     */
    @Test
    @SuppressWarnings("unchecked")
    void getMeNoToken_returns401() {
        ResponseEntity<Map> response = restTemplate.exchange(
            "/users/me", HttpMethod.GET, HttpEntity.EMPTY, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * AUTH-04: GET /api/users/me with a malformed token returns 401 (T-02-08).
     */
    @Test
    @SuppressWarnings("unchecked")
    void malformedToken_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer not-a-real-jwt");
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
            "/users/me", HttpMethod.GET, request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * AUTH-04 + Pitfall 4 (T-02-10): Authenticated response must not set a JSESSIONID cookie
     * — confirms STATELESS session policy is active.
     */
    @Test
    @SuppressWarnings("unchecked")
    void authenticatedResponse_noJsessionidCookie() {
        // Register and get a token
        Map<String, Object> registerBody = Map.of(
            "email",       "frank@example.com",
            "password",    "securepassword123",
            "displayName", "Frank"
        );
        ResponseEntity<Map> registerResponse = restTemplate.postForEntity(
            "/auth/register", registerBody, Map.class);
        String token = registerResponse.getBody().get("token").toString();

        // Make authenticated request
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
            "/users/me", HttpMethod.GET, request, Map.class);

        // No Set-Cookie: JSESSIONID= header (T-02-10, Pitfall 4)
        assertThat(response.getHeaders().get("Set-Cookie")).satisfiesAnyOf(
            cookies -> assertThat(cookies).isNull(),
            cookies -> assertThat(cookies.stream().noneMatch(c -> c.startsWith("JSESSIONID"))).isTrue()
        );
    }
}
