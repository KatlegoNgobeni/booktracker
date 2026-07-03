package com.booktracker.social;

import com.booktracker.books.BookEntity;
import com.booktracker.books.BookRepository;
import com.booktracker.shelf.ShelfRepository;
import com.booktracker.shelf.ShelfStatus;
import com.booktracker.shelf.UserBookEntity;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the review-like endpoints (DISC-04).
 *
 * <p>Uses:
 * <ul>
 *   <li>{@code @SpringBootTest(RANDOM_PORT)} — full Spring context with real HTTP calls</li>
 *   <li>{@code @Testcontainers} + {@link PostgreSQLContainer} — real Postgres 16 DB (applies V4 migration)</li>
 *   <li>Direct repository seeding — BookRepository + ShelfRepository + UserRepository</li>
 * </ul>
 *
 * <p>Per-test unique user registration (AtomicInteger counter) ensures no cross-test
 * state contamination when tests share the same Testcontainers DB.
 *
 * <p>6 behaviors from the RESEARCH Validation Architecture (DISC-04) are covered:
 * <ul>
 *   <li>likeReturns201 — POST /api/entries/{entryId}/like → 201</li>
 *   <li>duplicateLikeReturns409 — same like twice → 409</li>
 *   <li>unlikeReturns204 — DELETE /api/entries/{entryId}/like → 204</li>
 *   <li>unlikeNotLikedReturns404 — unlike a non-liked entry → 404</li>
 *   <li>likeUnauthenticatedReturns401 — no JWT → 401</li>
 *   <li>profileEntryIncludesLikeCountAndLikedByMe — GET /api/users/{id}/profile → entry has likeCount + likedByMe</li>
 * </ul>
 *
 * <p><strong>RED phase (Task 1):</strong> Tests that hit like endpoints fail because
 * LikeController/LikeService do not exist yet. profileEntryIncludesLikeCountAndLikedByMe fails
 * because PublicShelfEntryDto lacks likeCount and likedByMe. Task 2 and Task 3 turn them GREEN.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LikeIntegrationTest {

    /** Shared PostgreSQL 16 container — started once for the class, applies V1-V4 migrations. */
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("booktracker_like_test")
                    .withUsername("bt_like_test")
                    .withPassword("bt_like_test_pw");

    /**
     * Wires Testcontainers JDBC URL/credentials and a test JWT secret into Spring
     * datasource properties so Flyway (including V4), Hibernate, and JwtUtil start correctly.
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
    private UserRepository userRepository;

    /** Current test user JWT token (set in @BeforeEach). */
    private String authToken;
    /** Current test user UUID (set in @BeforeEach). */
    private String currentUserId;

    /** Per-class counter — ensures unique email per test (shared DB, no per-test rollback). */
    private static final AtomicInteger testUserCounter = new AtomicInteger(0);
    private static final AtomicInteger testBookCounter = new AtomicInteger(0);

    /**
     * Register a unique "main" user before each test and capture the JWT + user ID.
     */
    @BeforeEach
    void setUp() {
        UserInfo info = registerUser("liketest");
        authToken = info.token;
        currentUserId = info.userId;
    }

    /**
     * Build HttpHeaders with Authorization: Bearer set to the current test user's token.
     */
    private HttpHeaders bearerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        return headers;
    }

    /**
     * Build HttpHeaders with Authorization: Bearer set to an arbitrary token.
     */
    private HttpHeaders bearerHeadersFor(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    /**
     * Register a new unique user and return their JWT token and user ID.
     *
     * @param prefix email prefix — counter appended to ensure uniqueness
     * @return UserInfo containing token and userId
     */
    @SuppressWarnings("unchecked")
    private UserInfo registerUser(String prefix) {
        String email = prefix + testUserCounter.getAndIncrement() + "@example.com";
        Map<String, Object> body = Map.of(
            "email",       email,
            "password",    "securepassword123",
            "displayName", "LikeTester"
        );
        Map<String, Object> response = restTemplate
                .postForEntity("/api/auth/register", body, Map.class)
                .getBody();
        String token = response.get("token").toString();
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) response.get("user");
        String userId = user.get("id").toString();
        return new UserInfo(token, userId);
    }

    /**
     * Seed a BookEntity directly into the DB (no Open Library call).
     */
    private BookEntity seedBook() {
        int num = testBookCounter.getAndIncrement();
        String olKeyShort = "OL" + num + "LW";
        String olKeyFull  = "/works/" + olKeyShort;

        return bookRepository.findByOpenLibraryKey(olKeyFull).orElseGet(() -> {
            BookEntity book = new BookEntity();
            book.setOpenLibraryKey(olKeyFull);
            book.setTitle("Like Test Book " + olKeyShort);
            book.setAuthors("Like Test Author");
            book.setCoverId("88888");
            book.setPageCount(250);
            return bookRepository.save(book);
        });
    }

    /**
     * Seed a READ UserBookEntity directly for the given userId.
     * Used by tests that need a target entry to like.
     *
     * @param userId       UUID of the user to seed the entry for
     * @param dateFinished the date the book was finished
     * @return the persisted UserBookEntity
     */
    private UserBookEntity seedReadEntry(String userId, LocalDate dateFinished) {
        BookEntity book = seedBook();
        com.booktracker.user.UserEntity user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("Test user not found: " + userId));

        UserBookEntity entry = new UserBookEntity();
        entry.setUser(user);
        entry.setBook(book);
        entry.setShelfStatus(ShelfStatus.READ);
        entry.setDateFinished(dateFinished);
        entry.setRating((short) 4);
        entry.setReview("A great read for like testing");
        return shelfRepository.save(entry);
    }

    // ----------------------------------------------------------------
    // DISC-04: Like / Unlike a review entry
    // ----------------------------------------------------------------

    /**
     * DISC-04: POST /api/entries/{entryId}/like with valid entry → 201 Created.
     *
     * <p>T-09-07 mitigation: the liker identity comes from JWT (@AuthenticationPrincipal),
     * never from the request body.
     */
    @Test
    @SuppressWarnings("unchecked")
    void likeReturns201() {
        UserInfo target = registerUser("liketarget");
        UserBookEntity entry = seedReadEntry(target.userId, LocalDate.now().minusDays(3));

        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/entries/" + entry.getId() + "/like",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /**
     * DISC-04: POST /api/entries/{entryId}/like twice → 409 Conflict.
     *
     * <p>review_likes_pair_uq constraint prevents duplicate (user_id, entry_id) rows.
     */
    @Test
    @SuppressWarnings("unchecked")
    void duplicateLikeReturns409() {
        UserInfo target = registerUser("dupliketarget");
        UserBookEntity entry = seedReadEntry(target.userId, LocalDate.now().minusDays(2));

        // First like — should succeed
        ResponseEntity<Map> first = restTemplate.exchange(
            "/api/entries/" + entry.getId() + "/like",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders()),
            Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second like — duplicate → 409
        ResponseEntity<Map> second = restTemplate.exchange(
            "/api/entries/" + entry.getId() + "/like",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders()),
            Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * DISC-04: DELETE /api/entries/{entryId}/like when liked → 204 No Content.
     *
     * <p>T-09-08 mitigation: unlike removes only the current user's own like row.
     */
    @Test
    void unlikeReturns204() {
        UserInfo target = registerUser("unliketarget");
        UserBookEntity entry = seedReadEntry(target.userId, LocalDate.now().minusDays(1));

        // Like first
        restTemplate.exchange(
            "/api/entries/" + entry.getId() + "/like",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        // Unlike
        ResponseEntity<Void> response = restTemplate.exchange(
            "/api/entries/" + entry.getId() + "/like",
            HttpMethod.DELETE,
            new HttpEntity<>(bearerHeaders()),
            Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /**
     * DISC-04: DELETE /api/entries/{entryId}/like when NOT liked → 404 Not Found.
     */
    @Test
    @SuppressWarnings("unchecked")
    void unlikeNotLikedReturns404() {
        UserInfo target = registerUser("notlikedtarget");
        UserBookEntity entry = seedReadEntry(target.userId, LocalDate.now().minusDays(4));

        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/entries/" + entry.getId() + "/like",
            HttpMethod.DELETE,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * DISC-04: POST /api/entries/{entryId}/like with no JWT → 401 Unauthorized.
     *
     * <p>T-09-07 mitigation: all like endpoints are behind JWT authentication.
     */
    @Test
    @SuppressWarnings("unchecked")
    void likeUnauthenticatedReturns401() {
        UserInfo target = registerUser("unauthliketarget");
        UserBookEntity entry = seedReadEntry(target.userId, LocalDate.now().minusDays(5));

        // No Authorization header
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/entries/" + entry.getId() + "/like",
            HttpMethod.POST,
            new HttpEntity<>(new HttpHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * DISC-04: GET /api/users/{id}/profile → readEntries each include likeCount and likedByMe.
     *
     * <p>Scenario: current user likes an entry on the target's public profile. The profile
     * response should show likeCount ≥ 1 and likedByMe true for that entry, and likedByMe
     * false for entries not liked by the viewer.
     */
    @Test
    @SuppressWarnings("unchecked")
    void profileEntryIncludesLikeCountAndLikedByMe() {
        UserInfo target = registerUser("profileliketarget");
        UserBookEntity entry = seedReadEntry(target.userId, LocalDate.now().minusDays(6));

        // Like the target's entry as the current user
        ResponseEntity<Map> likeResp = restTemplate.exchange(
            "/api/entries/" + entry.getId() + "/like",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders()),
            Map.class);
        assertThat(likeResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Fetch the target's public profile as the current user
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/users/" + target.userId + "/profile",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();

        Map<?, ?> readEntries = (Map<?, ?>) body.get("readEntries");
        assertThat(readEntries).isNotNull();
        List<?> content = (List<?>) readEntries.get("content");
        assertThat(content).isNotEmpty();

        // The liked entry should show likeCount ≥ 1 and likedByMe true
        Map<?, ?> entryDto = (Map<?, ?>) content.get(0);
        Object likeCountObj = entryDto.get("likeCount");
        Object likedByMeObj = entryDto.get("likedByMe");
        assertThat(likeCountObj).as("likeCount field should be present in entry dto").isNotNull();
        assertThat(likedByMeObj).as("likedByMe field should be present in entry dto").isNotNull();

        Integer likeCount = (Integer) likeCountObj;
        Boolean likedByMe = (Boolean) likedByMeObj;

        assertThat(likeCount).isGreaterThanOrEqualTo(1);
        assertThat(likedByMe).isTrue();
    }

    // ----------------------------------------------------------------
    // Private helper record
    // ----------------------------------------------------------------

    /** Simple carrier for a registered user's token and UUID. */
    private record UserInfo(String token, String userId) {}
}
