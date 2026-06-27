package com.booktracker.stats;

import com.booktracker.books.BookEntity;
import com.booktracker.books.BookRepository;
import com.booktracker.goal.GoalEntity;
import com.booktracker.goal.GoalRepository;
import com.booktracker.shelf.ShelfRepository;
import com.booktracker.shelf.ShelfStatus;
import com.booktracker.shelf.UserBookEntity;
import com.booktracker.user.UserEntity;
import com.booktracker.user.UserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the GET /api/stats endpoint (STATS-02).
 *
 * <p>Uses:
 * <ul>
 *   <li>{@code @SpringBootTest(RANDOM_PORT)} — full Spring context with real HTTP calls</li>
 *   <li>{@code @Testcontainers} + {@link PostgreSQLContainer} — real Postgres 16 DB</li>
 *   <li>Direct repository access to seed users, books, shelf entries, and goals without
 *       hitting external APIs</li>
 * </ul>
 *
 * <p>Tests covered:
 * <ul>
 *   <li>{@code statsReturns200WithFields} — basic fields (booksReadAllTime, booksReadThisYear,
 *       booksPerMonth 12-element array, goalTarget, goalProgressPercent)</li>
 *   <li>{@code unauthenticatedRequestReturns401} — T-05-01: no token → 401</li>
 *   <li>{@code nullPageCountEntryExcludedFromPagesRead} — D-09: book with null page_count
 *       does not contribute to pagesReadThisYear and is excluded from longestBook/shortestBook</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class StatsIntegrationTest {

    /** Shared PostgreSQL 16 container — started once for the class. */
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("booktracker_test")
                    .withUsername("bt_test")
                    .withPassword("bt_test_pw");

    /**
     * Wires Testcontainers JDBC URL/credentials and a test JWT secret into Spring properties.
     * Mirrors the exact pattern used in ShelfIntegrationTest (RESEARCH Code Example).
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled",      () -> "true");
        registry.add("jwt.secret", () -> "dGVzdC1zZWNyZXQtYmFzZTY0LWVuY29kZWQtMzJieXRlcw==");
        registry.add("openlibrary.validate-base-url", () -> "false");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ShelfRepository shelfRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** JWT token obtained per-test via user registration. */
    private String authToken;
    private UserEntity testUser;

    /** Per-class counters — ensures unique email + book keys across tests (shared DB). */
    private static final AtomicInteger testUserCounter = new AtomicInteger(100);
    private static final AtomicInteger testBookCounter = new AtomicInteger(100);

    @BeforeEach
    void setUp() {
        String email = "statstest" + testUserCounter.getAndIncrement() + "@example.com";
        Map<String, Object> registerBody = Map.of(
            "email",       email,
            "password",    "securepassword123",
            "displayName", "StatsTester"
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate
                .postForEntity("/api/auth/register", registerBody, Map.class)
                .getBody();
        authToken = response.get("token").toString();

        // Resolve the UserEntity via the registered email for direct DB operations
        testUser = userRepository.findByEmail(email).orElseThrow();
    }

    private HttpHeaders bearerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        return headers;
    }

    // ----------------------------------------------------------------
    // STATS-02: Full stats pipeline
    // ----------------------------------------------------------------

    /**
     * STATS-02: Seed a user, a book with page_count, a READ shelf entry finished this year,
     * and a goal. GET /api/stats returns 200 with all expected fields.
     */
    @Test
    @SuppressWarnings("unchecked")
    void statsReturns200WithFields() {
        // Seed a book with page count
        BookEntity book = seedBook(350, true);

        // Add a READ shelf entry finished this year
        seedShelfEntry(testUser, book, ShelfStatus.READ, LocalDate.of(LocalDate.now().getYear(), 4, 20));

        // Set a goal of 5 for the current year
        seedGoal(testUser, 5);

        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/stats", HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();

        // Core counts
        assertThat(body.get("booksReadAllTime")).isEqualTo(1);
        assertThat(body.get("booksReadThisYear")).isEqualTo(1);
        assertThat(body.get("currentlyReadingCount")).isEqualTo(0);

        // Goal fields
        assertThat(body.get("goalTarget")).isEqualTo(5);
        assertThat(body.get("goalProgressPercent")).isNotNull();

        // booksPerMonth: must be present and have 12 elements
        Object booksPerMonthRaw = body.get("booksPerMonth");
        assertThat(booksPerMonthRaw).isInstanceOf(List.class);
        List<?> booksPerMonth = (List<?>) booksPerMonthRaw;
        assertThat(booksPerMonth).hasSize(12);

        // April = index 3 should be 1
        assertThat(((Number) booksPerMonth.get(3)).intValue()).isEqualTo(1);
    }

    // ----------------------------------------------------------------
    // T-05-01: Unauthenticated request returns 401
    // ----------------------------------------------------------------

    /**
     * T-05-01: GET /api/stats without Authorization header returns 401 Unauthorized.
     * The endpoint is protected by anyRequest().authenticated() in SecurityConfig.
     */
    @Test
    void unauthenticatedRequestReturns401() {
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/stats", HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ----------------------------------------------------------------
    // D-09: null page_count entries excluded from pagesReadThisYear
    // ----------------------------------------------------------------

    /**
     * D-09 + D-10: A READ book whose book.page_count IS NULL does not contribute to
     * pagesReadThisYear and is excluded from longestBook/shortestBook.
     */
    @Test
    @SuppressWarnings("unchecked")
    void nullPageCountEntryExcludedFromPagesRead() {
        // Seed a book WITHOUT a page count
        BookEntity bookNoPages = seedBook(null, false);
        seedShelfEntry(testUser, bookNoPages, ShelfStatus.READ,
                LocalDate.of(LocalDate.now().getYear(), 2, 10));

        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/stats", HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();

        // pagesReadThisYear should be absent (null/omitted) since the only entry has no page count
        assertThat(body.get("pagesReadThisYear")).isNull();

        // longestBook and shortestBook should also be absent
        assertThat(body.get("longestBook")).isNull();
        assertThat(body.get("shortestBook")).isNull();
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /**
     * Seed a BookEntity directly into the DB. pageCount may be null.
     */
    private BookEntity seedBook(Integer pageCount, boolean withPages) {
        int num = testBookCounter.getAndIncrement();
        String olKey = "/works/OLstats" + num + "W";

        BookEntity book = new BookEntity();
        book.setOpenLibraryKey(olKey);
        book.setTitle("Stats Test Book " + num);
        book.setAuthors("Test Author");
        if (withPages && pageCount != null) {
            book.setPageCount(pageCount);
        }
        return bookRepository.save(book);
    }

    /**
     * Seed a UserBookEntity with the given status and dateFinished directly into the DB.
     */
    private UserBookEntity seedShelfEntry(UserEntity user, BookEntity book,
                                           ShelfStatus status, LocalDate dateFinished) {
        UserBookEntity entry = new UserBookEntity();
        entry.setUser(user);
        entry.setBook(book);
        entry.setShelfStatus(status);
        entry.setDateFinished(dateFinished);
        return shelfRepository.save(entry);
    }

    /**
     * Seed a GoalEntity for the given user and current year directly into the DB.
     */
    private GoalEntity seedGoal(UserEntity user, int targetCount) {
        GoalEntity goal = new GoalEntity();
        goal.setUser(user);
        goal.setYear(LocalDate.now().getYear());
        goal.setTargetCount(targetCount);
        return goalRepository.save(goal);
    }
}
