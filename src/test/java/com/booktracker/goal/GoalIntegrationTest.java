package com.booktracker.goal;

import org.junit.jupiter.api.BeforeEach;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Goal endpoints (STATS-01).
 *
 * <p>Uses:
 * <ul>
 *   <li>{@code @SpringBootTest(RANDOM_PORT)} — full Spring context with real HTTP calls</li>
 *   <li>{@code @Testcontainers} + {@link PostgreSQLContainer} — real Postgres 16 DB</li>
 *   <li>Per-test unique user registration (AtomicInteger counter) — no cross-test contamination</li>
 * </ul>
 *
 * <p>Tests covered:
 * <ul>
 *   <li>{@code setGoalThenGetGoal_roundTrips} — PUT then GET returns the persisted target</li>
 *   <li>{@code getGoal_beforeAnyGoalSet_returns404} — GET /goal before setting returns 404</li>
 *   <li>{@code getGoal_withoutAuth_returns401} — GET /goal without Bearer token returns 401</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GoalIntegrationTest {

    /** Shared PostgreSQL 16 container — started once for the class. */
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("booktracker_goal_test")
                    .withUsername("bt_goal_test")
                    .withPassword("bt_goal_test_pw");

    /**
     * Wires Testcontainers JDBC URL/credentials and a test JWT secret into the Spring
     * datasource properties so Flyway, Hibernate, and JwtUtil all start correctly.
     *
     * <p>Mirrors the DynamicPropertySource from ShelfIntegrationTest exactly, including
     * the test JWT secret and openlibrary.validate-base-url=false to disable SSRF guard.
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled",      () -> "true");
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQtYmFzZTY0LWVuY29kZWQtMzJieXRlcw==");
        // Disable SSRF guard so the context starts without a real Open Library URL
        registry.add("openlibrary.validate-base-url", () -> "false");
    }

    /**
     * TestRestTemplate makes real HTTP calls to the embedded server.
     * Base URL is automatically set to http://localhost:{randomPort}/api
     * (includes context-path) — test URLs must omit the /api prefix.
     */
    @Autowired
    private TestRestTemplate restTemplate;

    /** JWT token obtained per-test via a fresh user registration. */
    private String authToken;

    /** Per-class counter — ensures unique email per test (shared DB, no per-test rollback). */
    private static final AtomicInteger testUserCounter = new AtomicInteger(0);

    /**
     * Register a unique user before each test and capture the JWT.
     * Each test gets its own user so goal data does not bleed across tests.
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        String email = "goaltest" + testUserCounter.getAndIncrement() + "@example.com";
        Map<String, Object> registerBody = Map.of(
            "email",       email,
            "password",    "securepassword123",
            "displayName", "GoalTester"
        );
        Map<String, Object> response = restTemplate
                .postForEntity("/auth/register", registerBody, Map.class)
                .getBody();
        authToken = response.get("token").toString();
    }

    /**
     * Build HttpHeaders with Authorization: Bearer set.
     */
    private HttpHeaders bearerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        return headers;
    }

    // ----------------------------------------------------------------
    // STATS-01: PUT then GET round-trip
    // ----------------------------------------------------------------

    /**
     * STATS-01: PUT /goal with targetCount=12, then GET /goal returns 200 + the persisted
     * target for the current year.
     */
    @Test
    @SuppressWarnings("unchecked")
    void setGoalThenGetGoal_roundTrips() {
        // PUT /goal — set the goal
        Map<String, Object> putBody = Map.of("targetCount", 12);
        ResponseEntity<Map> putResponse = restTemplate.exchange(
            "/goal", HttpMethod.PUT,
            new HttpEntity<>(putBody, bearerHeaders()),
            Map.class);

        assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> putResult = putResponse.getBody();
        assertThat(putResult).isNotNull();
        assertThat(putResult.get("targetCount")).isEqualTo(12);

        // GET /goal — retrieve the goal
        ResponseEntity<Map> getResponse = restTemplate.exchange(
            "/goal", HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> getResult = getResponse.getBody();
        assertThat(getResult).isNotNull();
        assertThat(getResult.get("targetCount")).isEqualTo(12);
    }

    // ----------------------------------------------------------------
    // STATS-01: GET before any goal is set returns 404
    // ----------------------------------------------------------------

    /**
     * STATS-01 (D-02): GET /goal before any goal has been set returns 404 Not Found.
     */
    @Test
    @SuppressWarnings("unchecked")
    void getGoal_beforeAnyGoalSet_returns404() {
        ResponseEntity<Map> response = restTemplate.exchange(
            "/goal", HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ----------------------------------------------------------------
    // STATS-01: Unauthenticated request returns 401
    // ----------------------------------------------------------------

    /**
     * T-05-01: GET /goal without an Authorization header returns 401 Unauthorized.
     * Verifies that the anyRequest().authenticated() security rule covers /api/goal.
     */
    @Test
    @SuppressWarnings("unchecked")
    void getGoal_withoutAuth_returns401() {
        // No Authorization header — plain exchange with no headers
        ResponseEntity<Map> response = restTemplate.exchange(
            "/goal", HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
