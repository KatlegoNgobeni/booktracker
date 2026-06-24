package com.booktracker.books;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the book detail cache-or-fetch flow (BOOK-02 + BOOK-03).
 *
 * <p>Uses:
 * <ul>
 *   <li>{@code @SpringBootTest(RANDOM_PORT)} — full Spring context with real HTTP calls</li>
 *   <li>{@code @Testcontainers} + {@link PostgreSQLContainer} — real Postgres 16 DB</li>
 *   <li>{@code @EnableWireMock} — intercepts outbound Open Library calls</li>
 * </ul>
 *
 * <p>WireMock overrides {@code openlibrary.base-url} via
 * {@code baseUrlProperties = "openlibrary.base-url"} (Pitfall 6 — must match the
 * {@code @Value("${openlibrary.base-url}")} key in {@link OpenLibraryConfig} exactly).
 *
 * <p>Each test method uses a unique OL key to avoid cross-test DB state contamination
 * (the Testcontainers DB is shared across all tests in the class).
 *
 * <p>Tests covered:
 * <ul>
 *   <li>{@code detailCacheMiss} — first GET fetches from OL, persists a row (BOOK-02)</li>
 *   <li>{@code detailCacheHit} — second GET reads from DB; exactly one wire call total (D-02)</li>
 *   <li>{@code userAgent} — outbound work request carries the configured User-Agent (BOOK-03)</li>
 *   <li>{@code openLibrary404} — OL 404 maps to API 404 (BOOK-03)</li>
 * </ul>
 *
 * <p>All book endpoints require authentication — a JWT is obtained via the register
 * endpoint before each test (T-03-08 mitigation).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EnableWireMock({
    @ConfigureWireMock(
        name = "open-library",
        baseUrlProperties = "openlibrary.base-url"
    )
})
class BookCacheIntegrationTest {

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
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled",      () -> "true");
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQtYmFzZTY0LWVuY29kZWQtMzJieXRlcw==");
    }

    @InjectWireMock("open-library")
    WireMockServer wireMock;

    @Autowired
    private TestRestTemplate restTemplate;

    @Value("${openlibrary.user-agent:BookTracker/1.0 (contact@example.com)}")
    private String expectedUserAgent;

    @Autowired
    private BookRepository bookRepository;

    /** JWT token obtained per-test via a fresh user registration. */
    private String authToken;

    /** Counters for unique test data per test to avoid cross-test DB contamination. */
    private static final AtomicInteger testUserCounter = new AtomicInteger(0);
    private static final AtomicInteger testKeyCounter = new AtomicInteger(100);

    /** Per-test unique OL key suffix (short form, e.g. "OL45804W001"). */
    private String olKeyShort;
    /** Per-test full canonical OL key (e.g. "/works/OL45804W001"). */
    private String olKeyFull;

    @BeforeEach
    void setUp() {
        // Reset WireMock request journal before each test for clean assertion state
        wireMock.resetRequests();

        // Assign unique OL key for this test to avoid cross-test DB state contamination
        int keyNum = testKeyCounter.getAndIncrement();
        olKeyShort = "OL45804W" + keyNum;
        olKeyFull = "/works/OL45804W" + keyNum;

        // Register a unique test user and capture the JWT for authenticated book calls
        String email = "booktest" + testUserCounter.getAndIncrement() + "@example.com";
        Map<String, Object> registerBody = Map.of(
            "email",       email,
            "password",    "securepassword123",
            "displayName", "BookTester"
        );
        @SuppressWarnings("unchecked")
        ResponseEntity<Map> registerResponse = restTemplate.postForEntity(
            "/auth/register", registerBody, Map.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        authToken = registerResponse.getBody().get("token").toString();
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /** Build an HttpEntity with the Authorization: Bearer header attached. */
    private HttpEntity<Void> authenticatedRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        return new HttpEntity<>(headers);
    }

    /** Stub the OL works endpoint with a realistic work payload for the per-test key. */
    private void stubWorkEndpoint() {
        wireMock.stubFor(get(urlEqualTo("/works/" + olKeyShort + ".json"))
            .willReturn(okJson(String.format("""
                {
                  "key": "/works/%s",
                  "title": "Fantastic Mr Fox",
                  "description": "A story about a fox and his family",
                  "number_of_pages": 96,
                  "covers": [24195]
                }
                """, olKeyShort))));
    }

    // ----------------------------------------------------------------
    // BOOK-02: Cache miss → fetch + persist
    // ----------------------------------------------------------------

    /**
     * BOOK-02: First GET for an uncached olKey returns 200, the body matches
     * the stubbed work data, and a row now exists in the books table.
     */
    @Test
    @SuppressWarnings("unchecked")
    void detailCacheMiss_fetchesFromOpenLibraryAndPersists() {
        stubWorkEndpoint();

        ResponseEntity<Map> response = restTemplate.exchange(
            "/books/" + olKeyShort, HttpMethod.GET, authenticatedRequest(), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("title")).isEqualTo("Fantastic Mr Fox");
        assertThat(body.get("olKey")).isEqualTo(olKeyFull);
        assertThat(body.get("pageCount")).isEqualTo(96);

        // Verify the row was persisted to the books table using the full canonical key
        Optional<BookEntity> persisted = bookRepository.findByOpenLibraryKey(olKeyFull);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getTitle()).isEqualTo("Fantastic Mr Fox");
        assertThat(persisted.get().getPageCount()).isEqualTo(96);
    }

    // ----------------------------------------------------------------
    // BOOK-02 (D-02): Cache hit — exactly one outbound call
    // ----------------------------------------------------------------

    /**
     * D-02: A second GET for the same olKey returns 200 from the DB and WireMock
     * records exactly one outbound /works/{key}.json call total — proves caching.
     */
    @Test
    @SuppressWarnings("unchecked")
    void detailCacheHit_secondCallReadsDbNotWire() {
        stubWorkEndpoint();

        // First call — cache miss, fetches from OL
        ResponseEntity<Map> first = restTemplate.exchange(
            "/books/" + olKeyShort, HttpMethod.GET, authenticatedRequest(), Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second call — cache hit, should NOT call OL again
        ResponseEntity<Map> second = restTemplate.exchange(
            "/books/" + olKeyShort, HttpMethod.GET, authenticatedRequest(), Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().get("title")).isEqualTo("Fantastic Mr Fox");

        // Assert WireMock received exactly ONE outbound request (D-02)
        wireMock.verify(1, getRequestedFor(urlEqualTo("/works/" + olKeyShort + ".json")));
    }

    // ----------------------------------------------------------------
    // BOOK-03: User-Agent header on outbound call
    // ----------------------------------------------------------------

    /**
     * BOOK-03: The outbound work request to Open Library must carry the configured
     * User-Agent header from {@code openlibrary.user-agent}.
     */
    @Test
    void userAgent_headerPresentOnOutboundCall() {
        stubWorkEndpoint();

        restTemplate.exchange(
            "/books/" + olKeyShort, HttpMethod.GET, authenticatedRequest(), Map.class);

        // Assert the recorded request carried the expected User-Agent (read from config,
        // not hardcoded — CR-03 fix: email is now externalized to application.properties)
        wireMock.verify(1, getRequestedFor(urlEqualTo("/works/" + olKeyShort + ".json"))
            .withHeader("User-Agent", equalTo(expectedUserAgent)));
    }

    // ----------------------------------------------------------------
    // BOOK-03: Open Library 404 maps to API 404
    // ----------------------------------------------------------------

    /**
     * BOOK-03: A key that Open Library stubs as 404 must return 404 from our API.
     */
    @Test
    @SuppressWarnings("unchecked")
    void openLibrary404_returnsApiNotFound() {
        // Stub a different key as 404 (unique to this test — no prior row in DB)
        String notFoundKey = "OL00000W" + testKeyCounter.getAndIncrement();
        wireMock.stubFor(get(urlEqualTo("/works/" + notFoundKey + ".json"))
            .willReturn(notFound()));

        ResponseEntity<Map> response = restTemplate.exchange(
            "/books/" + notFoundKey, HttpMethod.GET, authenticatedRequest(), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
