package com.booktracker.notification;

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
 * Integration tests for the notification domain (NOTIF-01 persistence, NOTIF-03 REST).
 *
 * <p>Uses:
 * <ul>
 *   <li>{@code @SpringBootTest(RANDOM_PORT)} — full Spring context with real HTTP calls</li>
 *   <li>{@code @Testcontainers} + {@link PostgreSQLContainer} — real Postgres 16 DB (applies V1-V5 migrations)</li>
 *   <li>Direct repository/service seeding — NotificationService used to insert rows, then REST endpoints
 *       are asserted via TestRestTemplate</li>
 * </ul>
 *
 * <p>Per-test unique user registration (AtomicInteger counter) ensures no cross-test
 * state contamination when tests share the same Testcontainers DB.
 *
 * <p>5 behaviors covered (NOTIF-01 + NOTIF-03):
 * <ul>
 *   <li>createNotificationPersistsRow — NOTIF-01: NotificationService.createNotification persists a row</li>
 *   <li>unreadCountReflectsUnread — NOTIF-03: GET /api/notifications/unread-count returns correct count</li>
 *   <li>listReturnsNewestFirst — NOTIF-03: GET /api/notifications returns rows in createdAt DESC order</li>
 *   <li>readAllMarksAllReadAndUnreadCountZero — NOTIF-03: POST /api/notifications/read-all then unread-count is 0</li>
 *   <li>notificationsScopedToCurrentUser — NOTIF-03: user B's notifications do not appear for user A</li>
 * </ul>
 *
 * <p><strong>Plan 09-03 (persist-only — no WebSocket push):</strong>
 * Notifications are seeded by calling {@link NotificationService#createNotification} directly
 * (no triggers yet — they ship in Plan 09-04). Assertions are via REST endpoints.
 *
 * <p><strong>T-09-13 / T-09-15:</strong> Scoping tests verify that notification reads and
 * mark-all-read are scoped to the authenticated user's id from JWT — never from request params.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class NotificationIntegrationTest {

    /** Shared PostgreSQL 16 container — started once for the class, applies V1-V5 migrations. */
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("booktracker_notif_test")
                    .withUsername("bt_notif_test")
                    .withPassword("bt_notif_test_pw");

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
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ShelfRepository shelfRepository;

    /**
     * NotificationService is autowired to allow direct notification creation
     * without WebSocket triggers (those ship in Plan 09-04).
     */
    @Autowired
    private NotificationService notificationService;

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
        UserInfo info = registerUser("notiftest");
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
            "displayName", "NotifTester"
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
        String olKeyShort = "OL" + num + "NW";
        String olKeyFull  = "/works/" + olKeyShort;

        return bookRepository.findByOpenLibraryKey(olKeyFull).orElseGet(() -> {
            BookEntity book = new BookEntity();
            book.setOpenLibraryKey(olKeyFull);
            book.setTitle("Notif Test Book " + olKeyShort);
            book.setAuthors("Notif Test Author");
            book.setCoverId("77777");
            book.setPageCount(200);
            return bookRepository.save(book);
        });
    }

    /**
     * Seed a READ UserBookEntity directly for the given userId.
     */
    private UserBookEntity seedReadEntry(String userId) {
        BookEntity book = seedBook();
        com.booktracker.user.UserEntity user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("Test user not found: " + userId));

        UserBookEntity entry = new UserBookEntity();
        entry.setUser(user);
        entry.setBook(book);
        entry.setShelfStatus(ShelfStatus.READ);
        entry.setDateFinished(LocalDate.now().minusDays(5));
        entry.setRating((short) 4);
        entry.setReview("Notification test review");
        return shelfRepository.save(entry);
    }

    // ----------------------------------------------------------------
    // NOTIF-01: Notification persistence
    // ----------------------------------------------------------------

    /**
     * NOTIF-01: NotificationService.createNotification persists a NotificationEntity
     * row for a recipient/type/actor/entityId.
     *
     * <p>Verifies the row is retrievable via GET /api/notifications.
     */
    @Test
    @SuppressWarnings("unchecked")
    void createNotificationPersistsRow() {
        UserInfo actor = registerUser("createnotifactor");

        // Recipient is the current test user; actor is the other user
        com.booktracker.user.UserEntity recipient = userRepository.findById(UUID.fromString(currentUserId))
                .orElseThrow();
        com.booktracker.user.UserEntity actorEntity = userRepository.findById(UUID.fromString(actor.userId))
                .orElseThrow();

        // Seed a notification directly via the service (no WS trigger yet)
        notificationService.createNotification(recipient, NotificationType.FRIEND_REQUEST, actorEntity, null);

        // Assert the row is retrievable via REST
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/notifications",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        List<?> content = (List<?>) body.get("content");
        assertThat(content).isNotEmpty();

        Map<?, ?> item = (Map<?, ?>) content.get(0);
        assertThat(item.get("type")).isEqualTo("FRIEND_REQUEST");
        assertThat(item.get("actorId")).isEqualTo(actor.userId);
        assertThat(item.get("isRead")).isEqualTo(false);
    }

    // ----------------------------------------------------------------
    // NOTIF-03: Read API — unread-count, list, mark-all-read
    // ----------------------------------------------------------------

    /**
     * NOTIF-03: GET /api/notifications/unread-count returns the caller's unread count.
     *
     * <p>T-09-13 mitigation: unread count is scoped to @AuthenticationPrincipal.
     */
    @Test
    @SuppressWarnings("unchecked")
    void unreadCountReflectsUnread() {
        UserInfo actor = registerUser("countactor");

        com.booktracker.user.UserEntity recipient = userRepository.findById(UUID.fromString(currentUserId))
                .orElseThrow();
        com.booktracker.user.UserEntity actorEntity = userRepository.findById(UUID.fromString(actor.userId))
                .orElseThrow();

        // Create 2 notifications for the current user
        notificationService.createNotification(recipient, NotificationType.FRIEND_REQUEST, actorEntity, null);
        notificationService.createNotification(recipient, NotificationType.FRIEND_ACCEPTED, actorEntity, null);

        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/notifications/unread-count",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        Number count = (Number) body.get("count");
        assertThat(count).isNotNull();
        assertThat(count.longValue()).isGreaterThanOrEqualTo(2);
    }

    /**
     * NOTIF-03: GET /api/notifications returns the caller's notifications newest first.
     *
     * <p>Two notifications are created with a brief sleep between them; the newer one
     * must appear at index 0.
     */
    @Test
    @SuppressWarnings("unchecked")
    void listReturnsNewestFirst() throws InterruptedException {
        UserInfo actor = registerUser("orderactor");

        com.booktracker.user.UserEntity recipient = userRepository.findById(UUID.fromString(currentUserId))
                .orElseThrow();
        com.booktracker.user.UserEntity actorEntity = userRepository.findById(UUID.fromString(actor.userId))
                .orElseThrow();

        // Create older notification first
        notificationService.createNotification(recipient, NotificationType.FRIEND_REQUEST, actorEntity, null);
        // Small sleep so the second row gets a strictly later created_at
        Thread.sleep(20);
        // Create newer notification second
        notificationService.createNotification(recipient, NotificationType.REVIEW_LIKED, actorEntity, null);

        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/notifications",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        List<?> content = (List<?>) body.get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(2);

        // First item must be the newer one (REVIEW_LIKED)
        Map<?, ?> first = (Map<?, ?>) content.get(0);
        assertThat(first.get("type")).isEqualTo("REVIEW_LIKED");
    }

    /**
     * NOTIF-03: POST /api/notifications/read-all marks all the caller's notifications read;
     * then GET /api/notifications/unread-count returns 0.
     *
     * <p>T-09-15 mitigation: mark-read is scoped to the authenticated user's rows.
     */
    @Test
    @SuppressWarnings("unchecked")
    void readAllMarksAllReadAndUnreadCountZero() {
        UserInfo actor = registerUser("markreadactor");

        com.booktracker.user.UserEntity recipient = userRepository.findById(UUID.fromString(currentUserId))
                .orElseThrow();
        com.booktracker.user.UserEntity actorEntity = userRepository.findById(UUID.fromString(actor.userId))
                .orElseThrow();

        // Seed 2 unread notifications
        notificationService.createNotification(recipient, NotificationType.FRIEND_REQUEST, actorEntity, null);
        notificationService.createNotification(recipient, NotificationType.FRIEND_ACCEPTED, actorEntity, null);

        // Confirm count > 0 before mark-all-read
        ResponseEntity<Map> before = restTemplate.exchange(
            "/api/notifications/unread-count",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);
        assertThat(((Number) before.getBody().get("count")).longValue()).isGreaterThan(0);

        // Mark all as read
        ResponseEntity<Void> markResp = restTemplate.exchange(
            "/api/notifications/read-all",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeaders()),
            Void.class);
        assertThat(markResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Now unread-count must be 0
        ResponseEntity<Map> after = restTemplate.exchange(
            "/api/notifications/unread-count",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);
        assertThat(response(after)).isEqualTo(0L);
    }

    /**
     * Helper: extract count from the unread-count response body.
     */
    private long response(ResponseEntity<Map> resp) {
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = resp.getBody();
        assertThat(body).isNotNull();
        Number count = (Number) body.get("count");
        assertThat(count).isNotNull();
        return count.longValue();
    }

    /**
     * NOTIF-03 + T-09-13: Notification reads are scoped to the authenticated user —
     * user B's notifications do NOT appear when user A calls GET /api/notifications.
     *
     * <p>Procedure:
     * <ol>
     *   <li>Create a notification for user B (not for user A)</li>
     *   <li>Call GET /api/notifications as user A</li>
     *   <li>Assert A's list does not contain B's notification</li>
     * </ol>
     */
    @Test
    @SuppressWarnings("unchecked")
    void notificationsScopedToCurrentUser() {
        UserInfo userB = registerUser("scopeduserB");
        UserInfo actor = registerUser("scopeactor");

        com.booktracker.user.UserEntity userBEntity = userRepository.findById(UUID.fromString(userB.userId))
                .orElseThrow();
        com.booktracker.user.UserEntity actorEntity = userRepository.findById(UUID.fromString(actor.userId))
                .orElseThrow();

        // Create a notification for user B (not for the current test user A)
        notificationService.createNotification(userBEntity, NotificationType.FRIEND_REQUEST, actorEntity, null);

        // Fetch user A's notifications — must be empty (or not contain B's notification)
        ResponseEntity<Map> responseA = restTemplate.exchange(
            "/api/notifications",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);

        assertThat(responseA.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = responseA.getBody();
        List<?> content = (List<?>) body.get("content");

        // None of A's items should have actorId = actor with type FRIEND_REQUEST for B
        // Since no notification was created for A, A's list should be empty
        boolean containsBsNotification = content.stream()
            .anyMatch(item -> {
                Map<?, ?> m = (Map<?, ?>) item;
                return "FRIEND_REQUEST".equals(m.get("type")) &&
                       actor.userId.equals(m.get("actorId"));
            });
        assertThat(containsBsNotification)
            .as("User A should not see user B's notifications")
            .isFalse();

        // Also verify: user B fetching their own notifications DOES see the notification
        ResponseEntity<Map> responseB = restTemplate.exchange(
            "/api/notifications",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeadersFor(userB.token)),
            Map.class);

        assertThat(responseB.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> bodyB = responseB.getBody();
        List<?> contentB = (List<?>) bodyB.get("content");
        assertThat(contentB).isNotEmpty();
        Map<?, ?> firstB = (Map<?, ?>) contentB.get(0);
        assertThat(firstB.get("type")).isEqualTo("FRIEND_REQUEST");
    }

    // ----------------------------------------------------------------
    // NOTIF-01 trigger tests (Plan 09-04 — RED until triggers are wired)
    // ----------------------------------------------------------------

    /**
     * NOTIF-01 trigger: POST /api/friend-requests → a notification row is persisted
     * for the recipient with type FRIEND_REQUEST.
     *
     * <p>RED until FriendRequestService.sendRequest calls NotificationService.createNotification.
     */
    @Test
    @SuppressWarnings("unchecked")
    void notificationPersistedOnFriendRequest() {
        // sender = current test user; recipient = recipientUser
        UserInfo recipientUser = registerUser("frtriggerrecipient");

        // Sender sends a friend request to recipient
        Map<String, Object> sendBody = Map.of("recipientId", recipientUser.userId);
        ResponseEntity<Map> sendResp = restTemplate.exchange(
            "/api/friend-requests",
            HttpMethod.POST,
            new HttpEntity<>(sendBody, bearerHeaders()),
            Map.class);
        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Recipient checks their notifications — must have FRIEND_REQUEST notification
        ResponseEntity<Map> notifResp = restTemplate.exchange(
            "/api/notifications",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeadersFor(recipientUser.token)),
            Map.class);
        assertThat(notifResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<?> content = (List<?>) notifResp.getBody().get("content");
        assertThat(content).isNotEmpty();

        boolean hasFriendRequestNotif = content.stream().anyMatch(item -> {
            Map<?, ?> m = (Map<?, ?>) item;
            return "FRIEND_REQUEST".equals(m.get("type")) &&
                   currentUserId.equals(m.get("actorId"));
        });
        assertThat(hasFriendRequestNotif)
            .as("Recipient should have a FRIEND_REQUEST notification with sender as actor")
            .isTrue();
    }

    /**
     * NOTIF-01 trigger: PUT /api/friend-requests/{id}/accept → a notification row is persisted
     * for the original requester with type FRIEND_ACCEPTED.
     *
     * <p>RED until FriendRequestService.acceptRequest calls NotificationService.createNotification.
     */
    @Test
    @SuppressWarnings("unchecked")
    void notificationPersistedOnFriendAccepted() {
        // sender = current test user; recipient = acceptingUser
        UserInfo acceptingUser = registerUser("frtriggeracceptor");

        // Sender sends a friend request to the accepting user
        Map<String, Object> sendBody = Map.of("recipientId", acceptingUser.userId);
        ResponseEntity<Map> sendResp = restTemplate.exchange(
            "/api/friend-requests",
            HttpMethod.POST,
            new HttpEntity<>(sendBody, bearerHeaders()),
            Map.class);
        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String requestId = sendResp.getBody().get("id").toString();

        // Accepting user accepts the request
        ResponseEntity<Map> acceptResp = restTemplate.exchange(
            "/api/friend-requests/" + requestId + "/accept",
            HttpMethod.PUT,
            new HttpEntity<>(bearerHeadersFor(acceptingUser.token)),
            Map.class);
        assertThat(acceptResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Original sender (current user) checks their notifications — must have FRIEND_ACCEPTED notification
        ResponseEntity<Map> notifResp = restTemplate.exchange(
            "/api/notifications",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);
        assertThat(notifResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<?> content = (List<?>) notifResp.getBody().get("content");
        assertThat(content).isNotEmpty();

        boolean hasFriendAcceptedNotif = content.stream().anyMatch(item -> {
            Map<?, ?> m = (Map<?, ?>) item;
            return "FRIEND_ACCEPTED".equals(m.get("type")) &&
                   acceptingUser.userId.equals(m.get("actorId"));
        });
        assertThat(hasFriendAcceptedNotif)
            .as("Original requester should have a FRIEND_ACCEPTED notification from the acceptor")
            .isTrue();
    }

    /**
     * NOTIF-01 trigger: POST /api/entries/{entryId}/like → a notification row is persisted
     * for the entry owner with type REVIEW_LIKED.
     *
     * <p>RED until LikeService.likeReview calls NotificationService.createNotification.
     */
    @Test
    @SuppressWarnings("unchecked")
    void notificationPersistedOnReviewLiked() {
        // entryOwner = current test user; liker = another user
        UserInfo likerUser = registerUser("liketriggerliker");

        // Seed a READ entry for the current user (the review that will be liked)
        UserBookEntity entry = seedReadEntry(currentUserId);

        // liker likes the entry owner's review
        ResponseEntity<Void> likeResp = restTemplate.exchange(
            "/api/entries/" + entry.getId() + "/like",
            HttpMethod.POST,
            new HttpEntity<>(bearerHeadersFor(likerUser.token)),
            Void.class);
        assertThat(likeResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Entry owner (current user) checks their notifications — must have REVIEW_LIKED notification
        ResponseEntity<Map> notifResp = restTemplate.exchange(
            "/api/notifications",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);
        assertThat(notifResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<?> content = (List<?>) notifResp.getBody().get("content");
        assertThat(content).isNotEmpty();

        boolean hasReviewLikedNotif = content.stream().anyMatch(item -> {
            Map<?, ?> m = (Map<?, ?>) item;
            return "REVIEW_LIKED".equals(m.get("type")) &&
                   likerUser.userId.equals(m.get("actorId"));
        });
        assertThat(hasReviewLikedNotif)
            .as("Entry owner should have a REVIEW_LIKED notification with liker as actor")
            .isTrue();
    }

    /**
     * NOTIF-01 trigger: Mark a shelf entry READ (PATCH /api/entries/{entryId}/metadata) →
     * a notification row is persisted for each accepted friend with type FRIEND_FINISHED_BOOK.
     *
     * <p>Setup: A and B are accepted friends. A marks a book READ. B should receive
     * FRIEND_FINISHED_BOOK notification.
     *
     * <p>RED until ShelfService.updateMetadata calls NotificationService.createNotification
     * on READ status transitions.
     */
    @Test
    @SuppressWarnings("unchecked")
    void notificationPersistedOnBookFinished() {
        // friendUser = current test user; bookFinisher = another user
        UserInfo bookFinisher = registerUser("finishertrigger");

        // Establish friendship: bookFinisher sends request, current user accepts
        Map<String, Object> sendBody = Map.of("recipientId", currentUserId);
        ResponseEntity<Map> sendResp = restTemplate.exchange(
            "/api/friend-requests",
            HttpMethod.POST,
            new HttpEntity<>(sendBody, bearerHeadersFor(bookFinisher.token)),
            Map.class);
        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String requestId = sendResp.getBody().get("id").toString();

        // Current user accepts the request
        ResponseEntity<Map> acceptResp = restTemplate.exchange(
            "/api/friend-requests/" + requestId + "/accept",
            HttpMethod.PUT,
            new HttpEntity<>(bearerHeaders()),
            Map.class);
        assertThat(acceptResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Seed a CURRENTLY_READING entry for bookFinisher (will be updated to READ)
        BookEntity book = seedBook();
        com.booktracker.user.UserEntity finisher = userRepository.findById(UUID.fromString(bookFinisher.userId))
                .orElseThrow();
        UserBookEntity readingEntry = new UserBookEntity();
        readingEntry.setUser(finisher);
        readingEntry.setBook(book);
        readingEntry.setShelfStatus(ShelfStatus.CURRENTLY_READING);
        readingEntry = shelfRepository.save(readingEntry);

        // bookFinisher marks the entry READ via PATCH metadata
        Map<String, Object> metaBody = Map.of("status", "READ");
        ResponseEntity<Map> updateResp = restTemplate.exchange(
            "/api/entries/" + readingEntry.getId() + "/metadata",
            HttpMethod.PATCH,
            new HttpEntity<>(metaBody, bearerHeadersFor(bookFinisher.token)),
            Map.class);
        assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Current user (the friend) checks their notifications — must have FRIEND_FINISHED_BOOK notification
        ResponseEntity<Map> notifResp = restTemplate.exchange(
            "/api/notifications",
            HttpMethod.GET,
            new HttpEntity<>(bearerHeaders()),
            Map.class);
        assertThat(notifResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<?> content = (List<?>) notifResp.getBody().get("content");
        // Filter out FRIEND_ACCEPTED notification (generated in setUp) — look for FRIEND_FINISHED_BOOK
        boolean hasFriendFinishedNotif = content.stream().anyMatch(item -> {
            Map<?, ?> m = (Map<?, ?>) item;
            return "FRIEND_FINISHED_BOOK".equals(m.get("type")) &&
                   bookFinisher.userId.equals(m.get("actorId"));
        });
        assertThat(hasFriendFinishedNotif)
            .as("Friend of the book-finisher should have a FRIEND_FINISHED_BOOK notification")
            .isTrue();
    }

    // ----------------------------------------------------------------
    // Private helper record
    // ----------------------------------------------------------------

    /** Simple carrier for a registered user's token and UUID. */
    private record UserInfo(String token, String userId) {}
}
