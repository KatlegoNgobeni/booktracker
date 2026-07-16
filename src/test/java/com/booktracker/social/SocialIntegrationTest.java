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
 * Integration tests for the social endpoints (SOCIAL-01, SOCIAL-02, SOCIAL-03).
 *
 * <p>Uses:
 * <ul>
 *   <li>{@code @SpringBootTest(RANDOM_PORT)} — full Spring context with real HTTP calls</li>
 *   <li>{@code @Testcontainers} + {@link PostgreSQLContainer} — real Postgres 16 DB</li>
 *   <li>Direct repository seeding — BookRepository + ShelfRepository + UserRepository
 *       bypass Open Library and other network dependencies</li>
 * </ul>
 *
 * <p>Per-test unique user registration (AtomicInteger counter) ensures no cross-test
 * state contamination when tests share the same Testcontainers DB.
 *
 * <p>All 13 behaviors from the RESEARCH Validation Architecture are covered:
 * <ul>
 *   <li>SOCIAL-01 (follow/unfollow): followReturns201, selfFollowReturns400,
 *       duplicateFollowReturns409, followNonExistentReturns404, unfollowReturns204,
 *       unfollowNotFollowingReturns404, unauthFollowReturns401</li>
 *   <li>SOCIAL-02 (public profile): publicProfileReturnsReadEntries,
 *       publicProfileExcludesNonRead, publicProfileUnknownUserReturns404</li>
 *   <li>SOCIAL-03 (feed): feedShowsFolloweeBooks, emptyFeedWhenNoFollowees,
 *       feedOrderedByDateDesc</li>
 * </ul>
 *
 * <p><strong>RED phase:</strong> All 13 tests fail when SocialService and SocialController
 * do not yet exist (Task 1 creates this file). Task 2 (SocialService) and Task 3
 * (SocialController) turn them GREEN.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SocialIntegrationTest {

    /** Shared PostgreSQL 16 container — started once for the class. */
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("booktracker_test")
                    .withUsername("bt_test")
                    .withPassword("bt_test_pw");

    /**
     * Wires Testcontainers JDBC URL/credentials and a test JWT secret into Spring
     * datasource properties so Flyway, Hibernate, and JwtUtil start correctly.
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
     * Seeds an authenticated context for every test method.
     */
    @BeforeEach
    void setUp() {
        UserInfo info = registerUser("socialtest");
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
     * @param prefix email prefix (e.g. "socialtest") — counter appended to ensure uniqueness
     * @return UserInfo containing token and userId
     */
    @SuppressWarnings("unchecked")
    private UserInfo registerUser(String prefix) {
        String email = prefix + testUserCounter.getAndIncrement() + "@example.com";
        Map<String, Object> body = Map.of(
            "email",       email,
            "password",    "securepassword123",
            "displayName", "SocialTester"
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
     *
     * @return the seeded BookEntity
     */
    private BookEntity seedBook() {
        int num = testBookCounter.getAndIncrement();
        String olKeyShort = "OL" + num + "SW";
        String olKeyFull  = "/works/" + olKeyShort;

        return bookRepository.findByOpenLibraryKey(olKeyFull).orElseGet(() -> {
            BookEntity book = new BookEntity();
            book.setOpenLibraryKey(olKeyFull);
            book.setTitle("Social Test Book " + olKeyShort);
            book.setAuthors("Social Test Author");
            book.setCoverId("99999");
            book.setPageCount(300);
            return bookRepository.save(book);
        });
    }

    /**
     * Seed a READ UserBookEntity directly for the given userId, with the given dateFinished.
     * Used by tests that need a user to have READ shelf entries without going through the
     * full HTTP add + PATCH flow.
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
        entry.setReview("Test review for seeded entry");
        return shelfRepository.save(entry);
    }

    /**
     * Seed a non-READ UserBookEntity (WANT_TO_READ) directly for the given userId.
     * Used by publicProfileExcludesNonRead to verify private entries are hidden.
     *
     * @param userId UUID of the user to seed the entry for
     * @return the persisted UserBookEntity
     */
    private UserBookEntity seedWantToReadEntry(String userId) {
        BookEntity book = seedBook();
        com.booktracker.user.UserEntity user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("Test user not found: " + userId));

        UserBookEntity entry = new UserBookEntity();
        entry.setUser(user);
        entry.setBook(book);
        entry.setShelfStatus(ShelfStatus.WANT_TO_READ);
        return shelfRepository.save(entry);
    }

    // ----------------------------------------------------------------
    // SOCIAL-01: Follow / Unfollow
    // ----------------------------------------------------------------

    /**
     * SOCIAL-01: POST /api/users/{id}/follow with valid followee → 201 + {following:true}.
     */
    @Test
    @SuppressWarnings("unchecked")
    void followReturns201() {
        UserInfo other = registerUser("followee");

        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/users/" + other.userId + "/follow",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("following")).isEqualTo(true);
    }

    /**
     * SOCIAL-01: POST /api/users/{myOwnId}/follow → 400 (self-follow prevented at service layer).
     */
    @Test
    @SuppressWarnings("unchecked")
    void selfFollowReturns400() {
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/users/" + currentUserId + "/follow",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * SOCIAL-01 + Pitfall 1: Follow the same user twice → 409 + "Already following this user"
     * (NOT "Email already registered" from GlobalExceptionHandler).
     */
    @Test
    @SuppressWarnings("unchecked")
    void duplicateFollowReturns409() {
        UserInfo other = registerUser("dupfollowee");

        // First follow — should succeed
        ResponseEntity<Map> first = restTemplate.exchange(
            "/api/users/" + other.userId + "/follow",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders()),
            Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second follow — duplicate → 409
        ResponseEntity<Map> second = restTemplate.exchange(
            "/api/users/" + other.userId + "/follow",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders()),
            Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        Map<?, ?> body = second.getBody();
        assertThat(body).isNotNull();
        // Must NOT say "Email already registered" (Pitfall 1)
        assertThat(body.get("message")).isEqualTo("Already following this user");
    }

    /**
     * SOCIAL-01: POST /api/users/{randomUUID}/follow where user does not exist → 404.
     */
    @Test
    @SuppressWarnings("unchecked")
    void followNonExistentReturns404() {
        String randomId = UUID.randomUUID().toString();

        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/users/" + randomId + "/follow",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * SOCIAL-01: DELETE /api/users/{id}/follow when following → 204 No Content.
     */
    @Test
    void unfollowReturns204() {
        UserInfo other = registerUser("unfollowee");

        // Follow first
        restTemplate.exchange(
            "/api/users/" + other.userId + "/follow",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        // Unfollow
        ResponseEntity<Void> response = restTemplate.exchange(
            "/api/users/" + other.userId + "/follow",
            HttpMethod.DELETE,
            new HttpEntity<>(bearerHeaders()),
            Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /**
     * SOCIAL-01: DELETE /api/users/{id}/follow when NOT following → 404.
     */
    @Test
    @SuppressWarnings("unchecked")
    void unfollowNotFollowingReturns404() {
        UserInfo other = registerUser("notfollowee");

        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/users/" + other.userId + "/follow",
            HttpMethod.DELETE,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * SOCIAL-01: POST /api/users/{id}/follow with no JWT → 401 Unauthorized
     * (handled by existing SecurityConfig AuthenticationEntryPoint).
     */
    @Test
    @SuppressWarnings("unchecked")
    void unauthFollowReturns401() {
        UserInfo other = registerUser("unauthfollowee");

        // No Authorization header
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/users/" + other.userId + "/follow",
            HttpMethod.POST,
            new HttpEntity<>(new HttpHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ----------------------------------------------------------------
    // SOCIAL-02: Public Profile
    // ----------------------------------------------------------------

    /**
     * SOCIAL-02: GET /api/users/{id}/profile returns 200 with displayName and
     * paginated readEntries containing only READ entries.
     */
    @Test
    @SuppressWarnings("unchecked")
    void publicProfileReturnsReadEntries() {
        UserInfo target = registerUser("profiletarget");

        // Seed a READ entry for the target user
        seedReadEntry(target.userId, LocalDate.now().minusDays(5));

        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/users/" + target.userId + "/profile",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("displayName")).isNotNull();
        assertThat(body.get("userId")).isEqualTo(target.userId);
        assertThat(body.get("followerCount")).isNotNull();
        assertThat(body.get("followingCount")).isNotNull();

        Map<?, ?> readEntries = (Map<?, ?>) body.get("readEntries");
        assertThat(readEntries).isNotNull();
        List<?> content = (List<?>) readEntries.get("content");
        assertThat(content).isNotEmpty();
    }

    /**
     * SOCIAL-02 + T-08-03: GET /api/users/{id}/profile excludes WANT_TO_READ entries —
     * only READ entries appear in the public profile.
     */
    @Test
    @SuppressWarnings("unchecked")
    void publicProfileExcludesNonRead() {
        UserInfo target = registerUser("privacytarget");

        // Seed a WANT_TO_READ entry (should be excluded from public profile)
        seedWantToReadEntry(target.userId);

        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/users/" + target.userId + "/profile",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        Map<?, ?> readEntries = (Map<?, ?>) body.get("readEntries");
        List<?> content = (List<?>) readEntries.get("content");
        // WANT_TO_READ entry must not appear
        assertThat(content).isEmpty();
    }

    /**
     * SOCIAL-02: GET /api/users/{randomUUID}/profile where user does not exist → 404.
     */
    @Test
    @SuppressWarnings("unchecked")
    void publicProfileUnknownUserReturns404() {
        String randomId = UUID.randomUUID().toString();

        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/users/" + randomId + "/profile",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ----------------------------------------------------------------
    // SOCIAL-03: Activity Feed
    // ----------------------------------------------------------------

    /**
     * SOCIAL-03 + D-03: GET /api/feed returns 200 with READ entries from accepted friends.
     *
     * <p>Phase 9 switches the feed from follows to mutual friends (D-03). This test
     * establishes a friend relationship (send + accept) and verifies the friend's READ book
     * appears in the feed.
     */
    @Test
    @SuppressWarnings("unchecked")
    void feedShowsFolloweeBooks() {
        UserInfo friend = registerUser("feedfriend");

        // Seed a READ entry for the friend
        seedReadEntry(friend.userId, LocalDate.now().minusDays(3));

        // Current user sends a friend request; friend accepts (D-03 mutual friendship)
        Map<String, Object> reqBody = Map.of("recipientId", friend.userId);
        ResponseEntity<Map> sendResp = restTemplate.exchange(
            "/api/friend-requests",
            HttpMethod.POST,
            new HttpEntity<>(reqBody, bearerHeaders()),
            Map.class);
        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String requestId = sendResp.getBody().get("id").toString();

        restTemplate.exchange(
            "/api/friend-requests/" + requestId + "/accept",
            HttpMethod.PUT,
            new HttpEntity<>(bearerHeadersFor(friend.token)),
            Map.class);

        // Fetch feed — should show the friend's READ book
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/feed",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        List<?> content = (List<?>) body.get("content");
        assertThat(content).isNotEmpty();

        // Verify the feed item has expected fields
        Map<?, ?> item = (Map<?, ?>) content.get(0);
        assertThat(item.get("bookTitle")).isNotNull();
        assertThat(item.get("userId")).isEqualTo(friend.userId);
        assertThat(item.get("entryId")).isNotNull();
    }

    /**
     * SOCIAL-03 + D-03: GET /api/feed returns empty content when the current user has
     * no accepted friends (feed is now friends-based, not follows-based).
     */
    @Test
    @SuppressWarnings("unchecked")
    void emptyFeedWhenNoFollowees() {
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/feed",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        List<?> content = (List<?>) body.get("content");
        assertThat(content).isEmpty();
    }

    /**
     * SOCIAL-03 + D-03: GET /api/feed returns entries ordered by dateFinished DESC
     * (most recently finished first) — using accepted friends, not follows.
     */
    @Test
    @SuppressWarnings("unchecked")
    void feedOrderedByDateDesc() {
        UserInfo friendA = registerUser("feedorderfriendA");
        UserInfo friendB = registerUser("feedorderfriendB");

        // Seed entries with known dates — older entry first in DB, newer second
        LocalDate olderDate = LocalDate.now().minusDays(10);
        LocalDate newerDate = LocalDate.now().minusDays(2);
        seedReadEntry(friendA.userId, olderDate);
        seedReadEntry(friendB.userId, newerDate);

        // Establish accepted friendships with both (D-03 — mutual friendship required for feed)
        Map<String, Object> reqBodyA = Map.of("recipientId", friendA.userId);
        ResponseEntity<Map> sendA = restTemplate.exchange(
            "/api/friend-requests", HttpMethod.POST,
            new HttpEntity<>(reqBodyA, bearerHeaders()), Map.class);
        String requestIdA = sendA.getBody().get("id").toString();
        restTemplate.exchange("/api/friend-requests/" + requestIdA + "/accept",
            HttpMethod.PUT, new HttpEntity<>(bearerHeadersFor(friendA.token)), Map.class);

        Map<String, Object> reqBodyB = Map.of("recipientId", friendB.userId);
        ResponseEntity<Map> sendB = restTemplate.exchange(
            "/api/friend-requests", HttpMethod.POST,
            new HttpEntity<>(reqBodyB, bearerHeaders()), Map.class);
        String requestIdB = sendB.getBody().get("id").toString();
        restTemplate.exchange("/api/friend-requests/" + requestIdB + "/accept",
            HttpMethod.PUT, new HttpEntity<>(bearerHeadersFor(friendB.token)), Map.class);

        // Fetch feed
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/feed",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        List<?> content = (List<?>) body.get("content");
        assertThat(content).hasSize(2);

        // First item should have the newer date (DESC order)
        Map<?, ?> first = (Map<?, ?>) content.get(0);
        Map<?, ?> second = (Map<?, ?>) content.get(1);
        String firstDate = first.get("dateFinished").toString();
        String secondDate = second.get("dateFinished").toString();
        // Newer date string is lexicographically greater (ISO YYYY-MM-DD format)
        assertThat(firstDate.compareTo(secondDate)).isGreaterThan(0);
    }

    // ----------------------------------------------------------------
    // DISC-01: Friends-Reading Endpoint (Phase 15 — Wave 1 RED tests)
    // These tests will fail with 404 until Plan 02 adds GET /api/feed/friends-reading.
    // ----------------------------------------------------------------

    /**
     * DISC-01 + positive: GET /api/feed/friends-reading returns 200 with CURRENTLY_READING
     * entries from accepted friends.
     *
     * <p>Setup:
     * <ol>
     *   <li>Alice (the authenticated user) and Bob are registered</li>
     *   <li>Bob has a CURRENTLY_READING entry seeded directly via ShelfRepository</li>
     *   <li>Alice sends a friend request to Bob; Bob accepts it (ACCEPTED status)</li>
     *   <li>Alice calls GET /api/feed/friends-reading</li>
     * </ol>
     *
     * <p>Assertions:
     * <ul>
     *   <li>Response status is 200</li>
     *   <li>Response body has {@code content} array of length >= 1</li>
     *   <li>First item's {@code bookOlKey} matches the seeded book's short OL key</li>
     *   <li>First item's {@code bookTitle} matches the seeded book title</li>
     * </ul>
     *
     * <p><strong>RED phase:</strong> Fails with 404 until Plan 02 adds the endpoint.
     */
    @Test
    @SuppressWarnings("unchecked")
    void friendsReadingEndpointReturnsCurrentlyReadingBooks() {
        // Register Alice (current user already set up in @BeforeEach) and Bob
        UserInfo bob = registerUser("friendsreadbob");

        // Seed a CURRENTLY_READING entry for Bob
        UserBookEntity bobEntry = seedCurrentlyReadingEntry(bob.userId);
        String bobBookOlKey = extractShortOlKey(bobEntry.getBook().getOpenLibraryKey());
        String bobBookTitle = bobEntry.getBook().getTitle();

        // Alice sends a friend request to Bob; Bob accepts
        Map<String, Object> reqBody = Map.of("recipientId", bob.userId);
        ResponseEntity<Map> sendResp = restTemplate.exchange(
            "/api/friend-requests",
            HttpMethod.POST,
            new HttpEntity<>(reqBody, bearerHeaders()),
            Map.class);
        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String requestId = sendResp.getBody().get("id").toString();

        restTemplate.exchange(
            "/api/friend-requests/" + requestId + "/accept",
            HttpMethod.PUT,
            new HttpEntity<>(bearerHeadersFor(bob.token)),
            Map.class);

        // Alice calls GET /api/feed/friends-reading — should see Bob's CURRENTLY_READING book
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/feed/friends-reading",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        List<?> content = (List<?>) body.get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(1);

        // Verify the returned item matches Bob's seeded book
        Map<?, ?> item = (Map<?, ?>) content.get(0);
        assertThat(item.get("bookOlKey")).isEqualTo(bobBookOlKey);
        assertThat(item.get("bookTitle")).isEqualTo(bobBookTitle);
    }

    /**
     * DISC-01 + negative: GET /api/feed/friends-reading does NOT return books from non-friends.
     *
     * <p>Carol is registered and has a CURRENTLY_READING book. Alice has NO friend relationship
     * with Carol. Alice's friends-reading feed must not contain Carol's book.
     *
     * <p><strong>RED phase:</strong> Fails with 404 until Plan 02 adds the endpoint.
     */
    @Test
    @SuppressWarnings("unchecked")
    void friendsReadingDoesNotReturnNonFriendsBooks() {
        // Register Carol (no friend relationship with Alice)
        UserInfo carol = registerUser("friendsreadcarol");

        // Seed a CURRENTLY_READING entry for Carol
        UserBookEntity carolEntry = seedCurrentlyReadingEntry(carol.userId);
        String carolBookOlKey = extractShortOlKey(carolEntry.getBook().getOpenLibraryKey());

        // Alice calls GET /api/feed/friends-reading — should NOT see Carol's book
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/feed/friends-reading",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        List<?> content = (List<?>) body.get("content");

        // Carol's book olKey must NOT appear in any content item
        boolean carolBookPresent = content.stream()
            .filter(i -> i instanceof Map)
            .map(i -> (Map<?, ?>) i)
            .anyMatch(i -> carolBookOlKey.equals(i.get("bookOlKey")));
        assertThat(carolBookPresent).isFalse();
    }

    /**
     * Seed a CURRENTLY_READING {@link UserBookEntity} directly for the given userId.
     * Mirrors {@link #seedReadEntry} but uses {@link ShelfStatus#CURRENTLY_READING}.
     *
     * @param userId UUID string of the user to seed the entry for
     * @return the persisted UserBookEntity
     */
    private UserBookEntity seedCurrentlyReadingEntry(String userId) {
        BookEntity book = seedBook();
        com.booktracker.user.UserEntity user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("Test user not found: " + userId));

        UserBookEntity entry = new UserBookEntity();
        entry.setUser(user);
        entry.setBook(book);
        entry.setShelfStatus(ShelfStatus.CURRENTLY_READING);
        entry.setCurrentPage(50);
        return shelfRepository.save(entry);
    }

    /**
     * Extract the short OL key (e.g. "OL5SW") from the full OL key stored in the DB
     * (e.g. "/works/OL5SW"). The friends-reading DTO returns the short form.
     *
     * @param fullKey the full Open Library key as stored in BookEntity (e.g. "/works/OL5SW")
     * @return the short form (everything after the last "/")
     */
    private String extractShortOlKey(String fullKey) {
        int slash = fullKey.lastIndexOf('/');
        return slash >= 0 ? fullKey.substring(slash + 1) : fullKey;
    }

    // ----------------------------------------------------------------
    // Private helper record
    // ----------------------------------------------------------------

    /** Simple carrier for a registered user's token and UUID. */
    private record UserInfo(String token, String userId) {}
}
