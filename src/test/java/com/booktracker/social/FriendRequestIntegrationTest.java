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
 * Integration tests for the friend-request social layer (DISC-01, DISC-02, DISC-03).
 *
 * <p>Uses:
 * <ul>
 *   <li>{@code @SpringBootTest(RANDOM_PORT)} — full Spring context with real HTTP calls</li>
 *   <li>{@code @Testcontainers} + {@link PostgreSQLContainer} — real Postgres 16 DB (applies V3 migration)</li>
 *   <li>Direct repository seeding — BookRepository + ShelfRepository + UserRepository</li>
 * </ul>
 *
 * <p>Per-test unique user registration (AtomicInteger counter) ensures no cross-test
 * state contamination when tests share the same Testcontainers DB.
 *
 * <p>All 12 behaviors from the RESEARCH Validation Architecture are covered:
 * <ul>
 *   <li>DISC-01 (user search): userSearchReturnsMatchExcludingSelf, userSearchResultCarriesFriendStatus</li>
 *   <li>DISC-02 (friend requests): sendRequestReturns201AndPending, selfRequestReturns400,
 *       duplicateActiveRelationshipReturns409, acceptByRecipientReturns200Accepted,
 *       acceptByNonRecipientReturns403, rejectByRecipientReturns200Rejected,
 *       cancelByRequesterReturns204, cancelByNonRequesterReturns403,
 *       feedAfterAcceptShowsFriendReadBooks</li>
 *   <li>DISC-03 (pending widget): pendingReceivedReturnsIncoming</li>
 * </ul>
 *
 * <p><strong>RED phase:</strong> Tests that hit friend-request/search endpoints fail with 404 or
 * 401 because the controller/service do not exist yet. Task 2 and Task 3 turn them GREEN.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class FriendRequestIntegrationTest {

    /** Shared PostgreSQL 16 container — started once for the class. */
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("booktracker_fr_test")
                    .withUsername("bt_fr_test")
                    .withPassword("bt_fr_test_pw");

    /**
     * Wires Testcontainers JDBC URL/credentials and a test JWT secret into Spring
     * datasource properties so Flyway (including V3), Hibernate, and JwtUtil start correctly.
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
    private static final AtomicInteger testBookCounter = new AtomicInteger(1000);

    /**
     * Register a unique "main" user before each test and capture the JWT + user ID.
     */
    @BeforeEach
    void setUp() {
        UserInfo info = registerUser("frtest");
        authToken = info.token;
        currentUserId = info.userId;
    }

    // ----------------------------------------------------------------
    // Helper methods
    // ----------------------------------------------------------------

    /** Build HttpHeaders with Authorization: Bearer set to the current user's token. */
    private HttpHeaders bearerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        return headers;
    }

    /** Build HttpHeaders with Authorization: Bearer set to an arbitrary token. */
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
            "displayName", "FRTester-" + email.split("@")[0]
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
        String olKeyShort = "OL" + num + "FRW";
        String olKeyFull  = "/works/" + olKeyShort;

        return bookRepository.findByOpenLibraryKey(olKeyFull).orElseGet(() -> {
            BookEntity book = new BookEntity();
            book.setOpenLibraryKey(olKeyFull);
            book.setTitle("FR Test Book " + olKeyShort);
            book.setAuthors("FR Test Author");
            book.setCoverId("88888");
            book.setPageCount(250);
            return bookRepository.save(book);
        });
    }

    /**
     * Seed a READ UserBookEntity directly for the given userId with the given dateFinished.
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
        entry.setReview("Test review for FR seeded entry");
        return shelfRepository.save(entry);
    }

    // ----------------------------------------------------------------
    // DISC-02: Send friend request
    // ----------------------------------------------------------------

    /**
     * DISC-02: POST /api/friend-requests with valid recipientId → 201 + {status:"PENDING"}.
     */
    @Test
    @SuppressWarnings("unchecked")
    void sendRequestReturns201AndPending() {
        UserInfo recipient = registerUser("recipient");

        Map<String, Object> body = Map.of("recipientId", recipient.userId);
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/friend-requests",
            HttpMethod.POST,
            new HttpEntity<>(body, bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.get("status")).isEqualTo("PENDING");
        assertThat(result.get("requesterId")).isEqualTo(currentUserId);
        assertThat(result.get("recipientId")).isEqualTo(recipient.userId);
    }

    /**
     * DISC-02 + T-09-06: POST /api/friend-requests to self → 400 (self-request prevented).
     */
    @Test
    @SuppressWarnings("unchecked")
    void selfRequestReturns400() {
        Map<String, Object> body = Map.of("recipientId", currentUserId);
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/friend-requests",
            HttpMethod.POST,
            new HttpEntity<>(body, bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * DISC-02 + Pitfall 5: POST /api/friend-requests when an active relationship exists → 409.
     */
    @Test
    @SuppressWarnings("unchecked")
    void duplicateActiveRelationshipReturns409() {
        UserInfo recipient = registerUser("dup-recipient");

        Map<String, Object> body = Map.of("recipientId", recipient.userId);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, bearerHeaders());

        // First request — should succeed
        ResponseEntity<Map> first = restTemplate.exchange(
            "/api/friend-requests", HttpMethod.POST, req, Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second request — duplicate active relationship → 409
        ResponseEntity<Map> second = restTemplate.exchange(
            "/api/friend-requests", HttpMethod.POST, req, Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ----------------------------------------------------------------
    // DISC-02: Accept friend request
    // ----------------------------------------------------------------

    /**
     * DISC-02 + T-09-01: PUT /api/friend-requests/{id}/accept by the recipient → 200 + ACCEPTED.
     */
    @Test
    @SuppressWarnings("unchecked")
    void acceptByRecipientReturns200Accepted() {
        UserInfo requester = registerUser("accept-requester");

        // requester sends request to current user (current user = recipient)
        Map<String, Object> body = Map.of("recipientId", currentUserId);
        ResponseEntity<Map> sendResp = restTemplate.exchange(
            "/api/friend-requests",
            HttpMethod.POST,
            new HttpEntity<>(body, bearerHeadersFor(requester.token)),
            Map.class);
        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String requestId = sendResp.getBody().get("id").toString();

        // Current user (recipient) accepts
        ResponseEntity<Map> acceptResp = restTemplate.exchange(
            "/api/friend-requests/" + requestId + "/accept",
            HttpMethod.PUT,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(acceptResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(acceptResp.getBody().get("status")).isEqualTo("ACCEPTED");
    }

    /**
     * DISC-02 + T-09-01: PUT /api/friend-requests/{id}/accept by someone who is NOT the
     * recipient → 403 (IDOR guard).
     */
    @Test
    @SuppressWarnings("unchecked")
    void acceptByNonRecipientReturns403() {
        UserInfo requester = registerUser("non-rec-requester");
        UserInfo actualRecipient = registerUser("non-rec-recipient");
        UserInfo attacker = registerUser("non-rec-attacker");

        // requester sends to actualRecipient
        Map<String, Object> body = Map.of("recipientId", actualRecipient.userId);
        ResponseEntity<Map> sendResp = restTemplate.exchange(
            "/api/friend-requests",
            HttpMethod.POST,
            new HttpEntity<>(body, bearerHeadersFor(requester.token)),
            Map.class);
        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String requestId = sendResp.getBody().get("id").toString();

        // Attacker (not the recipient) tries to accept → 403
        ResponseEntity<Map> attackResp = restTemplate.exchange(
            "/api/friend-requests/" + requestId + "/accept",
            HttpMethod.PUT,
            new HttpEntity<>(bearerHeadersFor(attacker.token)),
            Map.class);

        assertThat(attackResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ----------------------------------------------------------------
    // DISC-02: Reject friend request
    // ----------------------------------------------------------------

    /**
     * DISC-02: PUT /api/friend-requests/{id}/reject by the recipient → 200 + REJECTED.
     */
    @Test
    @SuppressWarnings("unchecked")
    void rejectByRecipientReturns200Rejected() {
        UserInfo requester = registerUser("reject-requester");

        // requester sends request to current user (current user = recipient)
        Map<String, Object> body = Map.of("recipientId", currentUserId);
        ResponseEntity<Map> sendResp = restTemplate.exchange(
            "/api/friend-requests",
            HttpMethod.POST,
            new HttpEntity<>(body, bearerHeadersFor(requester.token)),
            Map.class);
        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String requestId = sendResp.getBody().get("id").toString();

        // Current user (recipient) rejects
        ResponseEntity<Map> rejectResp = restTemplate.exchange(
            "/api/friend-requests/" + requestId + "/reject",
            HttpMethod.PUT,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(rejectResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejectResp.getBody().get("status")).isEqualTo("REJECTED");
    }

    // ----------------------------------------------------------------
    // DISC-02: Cancel friend request
    // ----------------------------------------------------------------

    /**
     * DISC-02 + T-09-02: DELETE /api/friend-requests/{id} by the requester → 204 (row removed).
     */
    @Test
    void cancelByRequesterReturns204() {
        UserInfo recipient = registerUser("cancel-recipient");

        // Current user sends request (current user = requester)
        Map<String, Object> body = Map.of("recipientId", recipient.userId);
        @SuppressWarnings("unchecked")
        ResponseEntity<Map> sendResp = restTemplate.exchange(
            "/api/friend-requests",
            HttpMethod.POST,
            new HttpEntity<>(body, bearerHeaders()),
            Map.class);
        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String requestId = sendResp.getBody().get("id").toString();

        // Current user (requester) cancels
        ResponseEntity<Void> cancelResp = restTemplate.exchange(
            "/api/friend-requests/" + requestId,
            HttpMethod.DELETE,
            new HttpEntity<>(bearerHeaders()),
            Void.class);

        assertThat(cancelResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /**
     * DISC-02 + T-09-02: DELETE /api/friend-requests/{id} by someone who is NOT the
     * requester → 403 (IDOR guard).
     */
    @Test
    @SuppressWarnings("unchecked")
    void cancelByNonRequesterReturns403() {
        UserInfo recipient = registerUser("cancel-nr-recipient");
        UserInfo attacker = registerUser("cancel-nr-attacker");

        // Current user sends request (current user = requester)
        Map<String, Object> body = Map.of("recipientId", recipient.userId);
        ResponseEntity<Map> sendResp = restTemplate.exchange(
            "/api/friend-requests",
            HttpMethod.POST,
            new HttpEntity<>(body, bearerHeaders()),
            Map.class);
        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String requestId = sendResp.getBody().get("id").toString();

        // Attacker (not the requester) tries to cancel → 403
        ResponseEntity<Map> attackResp = restTemplate.exchange(
            "/api/friend-requests/" + requestId,
            HttpMethod.DELETE,
            new HttpEntity<>(bearerHeadersFor(attacker.token)),
            Map.class);

        assertThat(attackResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ----------------------------------------------------------------
    // DISC-03: Pending received (Feed discovery widget)
    // ----------------------------------------------------------------

    /**
     * DISC-03: GET /api/friend-requests/pending-received returns only PENDING requests
     * where the current user is the recipient.
     */
    @Test
    @SuppressWarnings("unchecked")
    void pendingReceivedReturnsIncoming() {
        UserInfo requesterA = registerUser("pending-req-a");
        UserInfo requesterB = registerUser("pending-req-b");

        // Both requesterA and requesterB send requests to current user (recipient)
        restTemplate.exchange("/api/friend-requests", HttpMethod.POST,
            new HttpEntity<>(Map.of("recipientId", currentUserId), bearerHeadersFor(requesterA.token)),
            Map.class);
        restTemplate.exchange("/api/friend-requests", HttpMethod.POST,
            new HttpEntity<>(Map.of("recipientId", currentUserId), bearerHeadersFor(requesterB.token)),
            Map.class);

        ResponseEntity<List> response = restTemplate.exchange(
            "/api/friend-requests/pending-received",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> pending = response.getBody();
        assertThat(pending).isNotNull();
        assertThat(pending).hasSizeGreaterThanOrEqualTo(2);

        // All returned items must be PENDING and directed to the current user
        for (Object item : pending) {
            Map<?, ?> req = (Map<?, ?>) item;
            assertThat(req.get("status")).isEqualTo("PENDING");
            assertThat(req.get("recipientId")).isEqualTo(currentUserId);
        }
    }

    // ----------------------------------------------------------------
    // DISC-01: User search
    // ----------------------------------------------------------------

    /**
     * DISC-01: GET /api/users/search?q=name returns matching users, excluding current user.
     */
    @Test
    @SuppressWarnings("unchecked")
    void userSearchReturnsMatchExcludingSelf() {
        // Register a user with a distinctive display name prefix
        UserInfo targetA = registerUser("searchable");
        UserInfo targetB = registerUser("searchable");

        // Search for "FRTester-searchable" — should find targetA and targetB but NOT self
        @SuppressWarnings("unchecked")
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/users/search?q=searchable",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        List<?> content = (List<?>) body.get("content");
        assertThat(content).isNotEmpty();

        // Current user must NOT appear in results
        boolean selfInResults = content.stream()
            .anyMatch(item -> currentUserId.equals(((Map<?, ?>) item).get("id")));
        assertThat(selfInResults).isFalse();

        // At least one of the "searchable" users should appear
        boolean targetFound = content.stream()
            .anyMatch(item -> {
                Object id = ((Map<?, ?>) item).get("id");
                return targetA.userId.equals(id) || targetB.userId.equals(id);
            });
        assertThat(targetFound).isTrue();
    }

    /**
     * DISC-01: GET /api/users/search results include a friendStatus field.
     */
    @Test
    @SuppressWarnings("unchecked")
    void userSearchResultCarriesFriendStatus() {
        UserInfo target = registerUser("friendstatus");

        // Send a friend request to target — so target should show PENDING_SENT
        Map<String, Object> body = Map.of("recipientId", target.userId);
        restTemplate.exchange("/api/friend-requests", HttpMethod.POST,
            new HttpEntity<>(body, bearerHeaders()), Map.class);

        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/users/search?q=friendstatus",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) response.getBody().get("content");
        assertThat(content).isNotEmpty();

        // Find our target in the results
        Map<?, ?> targetResult = content.stream()
            .filter(item -> target.userId.equals(((Map<?, ?>) item).get("id")))
            .map(item -> (Map<?, ?>) item)
            .findFirst()
            .orElse(null);

        assertThat(targetResult).isNotNull();
        // friendStatus should be present and be PENDING_SENT since we sent a request
        assertThat(targetResult.get("friendStatus")).isEqualTo("PENDING_SENT");
        assertThat(targetResult.get("requestId")).isNotNull();
    }

    // ----------------------------------------------------------------
    // DISC-02 + D-03: Feed after friends accept
    // ----------------------------------------------------------------

    /**
     * DISC-02 + D-03: GET /api/feed after mutual accept shows friend's READ books
     * bidirectionally (friend is recipient in the request).
     */
    @Test
    @SuppressWarnings("unchecked")
    void feedAfterAcceptShowsFriendReadBooks() {
        UserInfo friend = registerUser("feed-friend");

        // Seed a READ book for the friend
        seedReadEntry(friend.userId, LocalDate.now().minusDays(5));

        // Current user sends a friend request to friend
        Map<String, Object> body = Map.of("recipientId", friend.userId);
        ResponseEntity<Map> sendResp = restTemplate.exchange(
            "/api/friend-requests",
            HttpMethod.POST,
            new HttpEntity<>(body, bearerHeaders()),
            Map.class);
        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String requestId = sendResp.getBody().get("id").toString();

        // Friend accepts the request
        restTemplate.exchange(
            "/api/friend-requests/" + requestId + "/accept",
            HttpMethod.PUT,
            new HttpEntity<>(bearerHeadersFor(friend.token)),
            Map.class);

        // Current user's feed should now show the friend's READ book
        ResponseEntity<Map> feedResp = restTemplate.exchange(
            "/api/feed",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(feedResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) feedResp.getBody().get("content");
        assertThat(content).isNotEmpty();

        // The feed item should be from the friend
        Map<?, ?> item = (Map<?, ?>) content.get(0);
        assertThat(item.get("userId")).isEqualTo(friend.userId);
        assertThat(item.get("bookTitle")).isNotNull();
    }

    // ----------------------------------------------------------------
    // Private helper record
    // ----------------------------------------------------------------

    /** Simple carrier for a registered user's token and UUID. */
    private record UserInfo(String token, String userId) {}
}
