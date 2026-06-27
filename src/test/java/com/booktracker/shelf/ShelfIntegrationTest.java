package com.booktracker.shelf;

import com.booktracker.books.BookEntity;
import com.booktracker.books.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the shelf CRUD endpoints (SHELF-01, SHELF-02, SHELF-06).
 *
 * <p>Uses:
 * <ul>
 *   <li>{@code @SpringBootTest(RANDOM_PORT)} — full Spring context with real HTTP calls</li>
 *   <li>{@code @Testcontainers} + {@link PostgreSQLContainer} — real Postgres 16 DB</li>
 *   <li>No WireMock — {@link BookRepository} is autowired to seed book rows directly,
 *       avoiding the Open Library network dependency</li>
 * </ul>
 *
 * <p>Per-test unique user registration (AtomicInteger counter) ensures no cross-test
 * state contamination when tests share the same Testcontainers DB.
 *
 * <p>Tests covered:
 * <ul>
 *   <li>{@code addToShelfReturns201} — SHELF-01: POST adds book, returns 201 + entryId + title</li>
 *   <li>{@code addDuplicateReturns409} — SHELF-01 + D-03: duplicate returns 409 + "Book already on shelf"</li>
 *   <li>{@code listShelfReturnsPaginatedEntries} — SHELF-02: GET returns paginated list with inline book data</li>
 *   <li>{@code listFilteredByStatus} — SHELF-02 + D-09: status filter returns only matching entries</li>
 *   <li>{@code getEntryOwnershipReturns403} — SHELF-06 + T-04-01: GET /shelf/{id} returns 403 for wrong owner</li>
 *   <li>{@code getMissingEntryReturns404} — SHELF-06: GET /shelf/{random-uuid} returns 404</li>
 * </ul>
 *
 * <p><strong>URL convention:</strong> TestRestTemplate base URL includes the context-path
 * ({@code /api}) — test paths omit {@code /api} prefix (e.g., {@code "/api/shelf"}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ShelfIntegrationTest {

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
     * <p>The test JWT secret is base64("test-secret-base64-encoded-32bytes") — 34 decoded
     * bytes, satisfying the ≥32-byte HMAC-SHA key requirement (D-07).
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled",      () -> "true");
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQtYmFzZTY0LWVuY29kZWQtMzJieXRlcw==");
        // WireMock not used; disable SSRF guard so the context starts without a real OL URL
        registry.add("openlibrary.validate-base-url", () -> "false");
    }

    /**
     * TestRestTemplate makes real HTTP calls to the embedded server.
     * Base URL is automatically set to http://localhost:{randomPort}
     * (no context-path since 07-01 refactor) — test URLs must include the /api prefix.
     */
    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * Autowired to seed BookEntity rows directly — avoids Open Library network dependency.
     * The openLibraryKey matches "/works/<olKey>" so ShelfService.addToShelf can resolve the FK.
     */
    @Autowired
    private BookRepository bookRepository;

    /** JWT token obtained per-test via a fresh user registration. */
    private String authToken;

    /** Per-class counter — ensures unique email per test (shared DB, no per-test rollback). */
    private static final AtomicInteger testUserCounter = new AtomicInteger(0);
    private static final AtomicInteger testBookCounter = new AtomicInteger(0);

    /**
     * Register a unique user before each test and capture the JWT.
     * Seeds an authenticated context for every test method.
     */
    @BeforeEach
    void setUp() {
        String email = "shelftest" + testUserCounter.getAndIncrement() + "@example.com";
        Map<String, Object> registerBody = Map.of(
            "email",       email,
            "password",    "securepassword123",
            "displayName", "ShelfTester"
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate
                .postForEntity("/api/auth/register", registerBody, Map.class)
                .getBody();
        authToken = response.get("token").toString();
    }

    /**
     * Build an HttpHeaders with Authorization: Bearer set.
     */
    private HttpHeaders bearerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        return headers;
    }

    /**
     * Seed a BookEntity directly into the DB (no Open Library call) and return the
     * short-form olKey (e.g. "OL12345W") that ShelfService expects in the request body.
     * The book is stored with full-form key "/works/OL12345W" to match getOrFetch behavior.
     */
    private String seedBook(String suffix) {
        int num = testBookCounter.getAndIncrement();
        String olKeyShort = "OL" + num + suffix + "W";
        String olKeyFull  = "/works/" + olKeyShort;

        // Only seed if not already present (tests share the same Testcontainers DB)
        if (bookRepository.findByOpenLibraryKey(olKeyFull).isEmpty()) {
            BookEntity book = new BookEntity();
            book.setOpenLibraryKey(olKeyFull);
            book.setTitle("Test Book " + olKeyShort);
            book.setAuthors("Test Author");
            book.setCoverId("12345");
            book.setPageCount(200);
            bookRepository.save(book);
        }
        return olKeyShort;
    }

    // ----------------------------------------------------------------
    // SHELF-01: Add to shelf
    // ----------------------------------------------------------------

    /**
     * SHELF-01 + D-01 + D-02: POST /shelf with valid {olKey, status} returns 201 Created
     * with a body containing entryId and the book's title.
     */
    @Test
    @SuppressWarnings("unchecked")
    void addToShelfReturns201() {
        String olKey = seedBook("A");
        Map<String, Object> body = Map.of("olKey", olKey, "status", "WANT_TO_READ");

        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/shelf", HttpMethod.POST,
            new HttpEntity<>(body, bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> responseBody = response.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("entryId")).isNotNull();
        assertThat(responseBody.get("title")).isEqualTo("Test Book OL" +
            (testBookCounter.get() - 1) + "AW");
    }

    /**
     * SHELF-01 + D-03 + T-04-02: Adding the same book twice for the same user returns
     * 409 Conflict with body message "Book already on shelf" (NOT "Email already registered").
     */
    @Test
    @SuppressWarnings("unchecked")
    void addDuplicateReturns409() {
        String olKey = seedBook("B");
        Map<String, Object> body = Map.of("olKey", olKey, "status", "WANT_TO_READ");
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, bearerHeaders());

        // First add — should succeed
        ResponseEntity<Map> first = restTemplate.exchange(
            "/api/shelf", HttpMethod.POST, request, Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second add — same user + same book → 409
        ResponseEntity<Map> second = restTemplate.exchange(
            "/api/shelf", HttpMethod.POST, request, Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        Map<?, ?> errorBody = second.getBody();
        assertThat(errorBody).isNotNull();
        // T-04-02: Must NOT say "Email already registered"
        assertThat(errorBody.get("message")).isEqualTo("Book already on shelf");
    }

    // ----------------------------------------------------------------
    // SHELF-02: List shelf (paginated + inline book data)
    // ----------------------------------------------------------------

    /**
     * SHELF-02 + D-08: GET /shelf returns a paginated response with inline book summary
     * (title, olKey) for each entry. Adding 2 books produces 2 entries.
     */
    @Test
    @SuppressWarnings("unchecked")
    void listShelfReturnsPaginatedEntries() {
        String olKey1 = seedBook("C");
        String olKey2 = seedBook("D");

        // Add two books
        restTemplate.exchange("/api/shelf", HttpMethod.POST,
            new HttpEntity<>(Map.of("olKey", olKey1, "status", "WANT_TO_READ"), bearerHeaders()),
            Map.class);
        restTemplate.exchange("/api/shelf", HttpMethod.POST,
            new HttpEntity<>(Map.of("olKey", olKey2, "status", "READ"), bearerHeaders()),
            Map.class);

        // List shelf
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/shelf", HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();

        List<?> content = (List<?>) body.get("content");
        assertThat(content).hasSize(2);

        // Each entry should include inline book summary fields
        Map<?, ?> entry = (Map<?, ?>) content.get(0);
        assertThat(entry.get("title")).isNotNull();
        assertThat(entry.get("olKey")).isNotNull();
        assertThat(entry.get("entryId")).isNotNull();
    }

    /**
     * SHELF-02 + D-09: GET /shelf?status=WANT_TO_READ returns only entries with that status.
     * Entries with other statuses are excluded from the response.
     */
    @Test
    @SuppressWarnings("unchecked")
    void listFilteredByStatus() {
        String olKey1 = seedBook("E");
        String olKey2 = seedBook("F");

        // Add one WANT_TO_READ and one READ
        restTemplate.exchange("/api/shelf", HttpMethod.POST,
            new HttpEntity<>(Map.of("olKey", olKey1, "status", "WANT_TO_READ"), bearerHeaders()),
            Map.class);
        restTemplate.exchange("/api/shelf", HttpMethod.POST,
            new HttpEntity<>(Map.of("olKey", olKey2, "status", "READ"), bearerHeaders()),
            Map.class);

        // Filter by WANT_TO_READ — should return exactly 1 entry
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/shelf?status=WANT_TO_READ", HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        List<?> content = (List<?>) body.get("content");
        assertThat(content).hasSize(1);

        Map<?, ?> entry = (Map<?, ?>) content.get(0);
        assertThat(entry.get("status")).isEqualTo("WANT_TO_READ");
    }

    // ----------------------------------------------------------------
    // SHELF-06: Ownership enforcement on GET /shelf/{id}
    // ----------------------------------------------------------------

    /**
     * SHELF-06 + T-04-01 (IDOR): GET /shelf/{id} with a token belonging to a different user
     * returns 403 Forbidden (NOT 404 — the entry exists, ownership check fails).
     *
     * <p>This tests the two-step ownership check in ShelfService.getEntryForUser:
     * findById → 404 if absent; equality check → 403 if mismatch.
     */
    @Test
    @SuppressWarnings("unchecked")
    void getEntryOwnershipReturns403() {
        // User A adds a book and captures the entryId
        String olKey = seedBook("G");
        ResponseEntity<Map> addResponse = restTemplate.exchange(
            "/api/shelf", HttpMethod.POST,
            new HttpEntity<>(Map.of("olKey", olKey, "status", "WANT_TO_READ"), bearerHeaders()),
            Map.class);
        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String entryId = addResponse.getBody().get("entryId").toString();

        // Register User B
        String emailB = "shelftest_b" + testUserCounter.getAndIncrement() + "@example.com";
        Map<String, Object> registerB = Map.of(
            "email",       emailB,
            "password",    "securepassword123",
            "displayName", "ShelfTesterB"
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> responseB = restTemplate
                .postForEntity("/api/auth/register", registerB, Map.class)
                .getBody();
        String tokenB = responseB.get("token").toString();

        // User B tries to GET User A's entry → should get 403
        HttpHeaders headersB = new HttpHeaders();
        headersB.setBearerAuth(tokenB);
        ResponseEntity<Map> getResponse = restTemplate.exchange(
            "/api/shelf/" + entryId, HttpMethod.GET,
            new HttpEntity<>(headersB),
            Map.class);

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * SHELF-06: GET /shelf/{random-uuid} with a UUID that does not exist returns 404.
     */
    @Test
    @SuppressWarnings("unchecked")
    void getMissingEntryReturns404() {
        String randomId = UUID.randomUUID().toString();
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/shelf/" + randomId, HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ----------------------------------------------------------------
    // SHELF-03: Update metadata (PATCH /shelf/{id})
    // ----------------------------------------------------------------

    /**
     * SHELF-03 + D-04 + D-05 + D-10: PATCH /shelf/{id} with {status:READ, rating:5, review:"great"}
     * returns 200 + body with rating==5 and dateFinished present (auto-set by D-10).
     */
    @Test
    @SuppressWarnings("unchecked")
    void updateMetadataReturns200() {
        String olKey = seedBook("H");
        // Add to shelf
        ResponseEntity<Map> addResponse = restTemplate.exchange(
            "/api/shelf", HttpMethod.POST,
            new HttpEntity<>(Map.of("olKey", olKey, "status", "WANT_TO_READ"), bearerHeaders()),
            Map.class);
        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String entryId = addResponse.getBody().get("entryId").toString();

        // PATCH metadata: set status=READ, rating=5, review="great"
        Map<String, Object> patchBody = Map.of(
            "status", "READ",
            "rating", 5,
            "review", "great"
        );
        ResponseEntity<Map> patchResponse = restTemplate.exchange(
            "/api/shelf/" + entryId, HttpMethod.PATCH,
            new HttpEntity<>(patchBody, bearerHeaders()),
            Map.class);

        assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = patchResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("rating")).isEqualTo(5);
        assertThat(body.get("review")).isEqualTo("great");
        assertThat(body.get("status")).isEqualTo("READ");
        // D-10: dateFinished should be auto-set to today
        assertThat(body.get("dateFinished")).isNotNull();
    }

    // ----------------------------------------------------------------
    // SHELF-04: Update progress (PATCH /shelf/{id}/progress)
    // ----------------------------------------------------------------

    /**
     * SHELF-04 + D-04 + D-05: PATCH /shelf/{id}/progress with {currentPage:42}
     * returns 200 + body with currentPage==42.
     */
    @Test
    @SuppressWarnings("unchecked")
    void updateProgressReturns200() {
        String olKey = seedBook("I");
        ResponseEntity<Map> addResponse = restTemplate.exchange(
            "/api/shelf", HttpMethod.POST,
            new HttpEntity<>(Map.of("olKey", olKey, "status", "CURRENTLY_READING"), bearerHeaders()),
            Map.class);
        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String entryId = addResponse.getBody().get("entryId").toString();

        // PATCH progress
        Map<String, Object> progressBody = Map.of("currentPage", 42);
        ResponseEntity<Map> patchResponse = restTemplate.exchange(
            "/api/shelf/" + entryId + "/progress", HttpMethod.PATCH,
            new HttpEntity<>(progressBody, bearerHeaders()),
            Map.class);

        assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = patchResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("currentPage")).isEqualTo(42);
    }

    /**
     * T-04-07: PATCH /shelf/{id}/progress with {currentPage:-1} returns 400 Bad Request
     * (validated by @Min(0) on UpdateProgressRequest.currentPage).
     */
    @Test
    @SuppressWarnings("unchecked")
    void updateProgressNegativeReturns400() {
        String olKey = seedBook("J");
        ResponseEntity<Map> addResponse = restTemplate.exchange(
            "/api/shelf", HttpMethod.POST,
            new HttpEntity<>(Map.of("olKey", olKey, "status", "CURRENTLY_READING"), bearerHeaders()),
            Map.class);
        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String entryId = addResponse.getBody().get("entryId").toString();

        // PATCH progress with invalid negative value
        Map<String, Object> progressBody = Map.of("currentPage", -1);
        ResponseEntity<Map> patchResponse = restTemplate.exchange(
            "/api/shelf/" + entryId + "/progress", HttpMethod.PATCH,
            new HttpEntity<>(progressBody, bearerHeaders()),
            Map.class);

        assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ----------------------------------------------------------------
    // SHELF-05: Remove entry (DELETE /shelf/{id})
    // ----------------------------------------------------------------

    /**
     * SHELF-05 + D-06: DELETE /shelf/{id} removes the entry and returns 204.
     * Subsequent GET /shelf/{id} returns 404 confirming the entry is gone.
     */
    @Test
    @SuppressWarnings("unchecked")
    void removeEntryReturns204() {
        String olKey = seedBook("K");
        ResponseEntity<Map> addResponse = restTemplate.exchange(
            "/api/shelf", HttpMethod.POST,
            new HttpEntity<>(Map.of("olKey", olKey, "status", "WANT_TO_READ"), bearerHeaders()),
            Map.class);
        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String entryId = addResponse.getBody().get("entryId").toString();

        // DELETE the entry
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
            "/api/shelf/" + entryId, HttpMethod.DELETE,
            new HttpEntity<>(bearerHeaders()),
            Void.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Confirm it is gone — subsequent GET should return 404
        ResponseEntity<Map> getResponse = restTemplate.exchange(
            "/api/shelf/" + entryId, HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ----------------------------------------------------------------
    // SHELF-06 (write paths): Ownership enforcement on PATCH and DELETE
    // ----------------------------------------------------------------

    /**
     * T-04-06 IDOR: User A adds entry → User B PATCH /shelf/{id} → 403 Forbidden.
     */
    @Test
    @SuppressWarnings("unchecked")
    void patchOwnershipReturns403() {
        // User A adds entry
        String olKey = seedBook("L");
        ResponseEntity<Map> addResponse = restTemplate.exchange(
            "/api/shelf", HttpMethod.POST,
            new HttpEntity<>(Map.of("olKey", olKey, "status", "WANT_TO_READ"), bearerHeaders()),
            Map.class);
        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String entryId = addResponse.getBody().get("entryId").toString();

        // Register User B
        String tokenB = registerNewUser();

        // User B tries to PATCH User A's entry → 403
        HttpHeaders headersB = new HttpHeaders();
        headersB.setBearerAuth(tokenB);
        Map<String, Object> patchBody = Map.of("status", "READ");
        ResponseEntity<Map> patchResponse = restTemplate.exchange(
            "/api/shelf/" + entryId, HttpMethod.PATCH,
            new HttpEntity<>(patchBody, headersB),
            Map.class);

        assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * T-04-06 IDOR: User A adds entry → User B DELETE /shelf/{id} → 403 Forbidden.
     */
    @Test
    @SuppressWarnings("unchecked")
    void deleteOwnershipReturns403() {
        // User A adds entry
        String olKey = seedBook("M");
        ResponseEntity<Map> addResponse = restTemplate.exchange(
            "/api/shelf", HttpMethod.POST,
            new HttpEntity<>(Map.of("olKey", olKey, "status", "WANT_TO_READ"), bearerHeaders()),
            Map.class);
        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String entryId = addResponse.getBody().get("entryId").toString();

        // Register User B
        String tokenB = registerNewUser();

        // User B tries to DELETE User A's entry → 403
        HttpHeaders headersB = new HttpHeaders();
        headersB.setBearerAuth(tokenB);
        ResponseEntity<Map> deleteResponse = restTemplate.exchange(
            "/api/shelf/" + entryId, HttpMethod.DELETE,
            new HttpEntity<>(headersB),
            Map.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    /**
     * Register a fresh user and return their JWT token.
     * Uses the shared testUserCounter to ensure unique emails across tests.
     */
    @SuppressWarnings("unchecked")
    private String registerNewUser() {
        String email = "shelftest_b" + testUserCounter.getAndIncrement() + "@example.com";
        Map<String, Object> registerBody = Map.of(
            "email",       email,
            "password",    "securepassword123",
            "displayName", "ShelfTesterB"
        );
        Map<String, Object> response = restTemplate
                .postForEntity("/api/auth/register", registerBody, Map.class)
                .getBody();
        return response.get("token").toString();
    }
}
