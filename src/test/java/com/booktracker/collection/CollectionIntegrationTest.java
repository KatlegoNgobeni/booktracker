package com.booktracker.collection;

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

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the collections API (COLL-01/02/03/04).
 *
 * <p>Tests all 7 collection endpoints with green assertions:
 * <ul>
 *   <li>POST /api/collections — createCollection</li>
 *   <li>GET  /api/collections — getMyCollections</li>
 *   <li>GET  /api/collections/{id} — getCollectionDetail</li>
 *   <li>PATCH /api/collections/{id} — updateCollection</li>
 *   <li>DELETE /api/collections/{id} — deleteCollection</li>
 *   <li>POST /api/collections/{id}/books — addBook</li>
 *   <li>DELETE /api/collections/{id}/books/{olKey} — removeBook</li>
 * </ul>
 *
 * <p>Ownership guard (403) tested with a second user (bob) attempting mutations on alice's collection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CollectionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("booktracker_col_test")
                    .withUsername("bt_col_test")
                    .withPassword("bt_col_test_pw");

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

    private String aliceToken;
    private String bobToken;

    private static final AtomicInteger counter = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        UserInfo alice = registerUser("alice");
        aliceToken = alice.token;
        UserInfo bob = registerUser("bob");
        bobToken = bob.token;
    }

    // ----------------------------------------------------------------
    // Helper methods
    // ----------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private UserInfo registerUser(String prefix) {
        String email = prefix + counter.getAndIncrement() + "@example.com";
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "securepassword123",
                "displayName", "CollTest-" + prefix
        );
        Map<String, Object> response = restTemplate
                .postForEntity("/api/auth/register", body, Map.class)
                .getBody();
        String token = response.get("token").toString();
        return new UserInfo(token);
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private String createCollectionAndGetId(String name, boolean isPublic, String token) {
        Map<String, Object> body = Map.of("name", name, "isPublic", isPublic);
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/collections", HttpMethod.POST,
                new HttpEntity<>(body, bearerHeaders(token)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().get("id").toString();
    }

    // ----------------------------------------------------------------
    // Test cases
    // ----------------------------------------------------------------

    /**
     * COLL-01: POST /api/collections returns 201 with id, name, bookCount: 0.
     */
    @Test
    @SuppressWarnings("unchecked")
    void createCollection_returnsDto() {
        Map<String, Object> body = Map.of("name", "Favourites", "isPublic", false);
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/collections", HttpMethod.POST,
                new HttpEntity<>(body, bearerHeaders(aliceToken)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> result = resp.getBody();
        assertThat(result).isNotNull();
        assertThat(result.get("id")).isNotNull();
        assertThat(result.get("name")).isEqualTo("Favourites");
        assertThat(result.get("bookCount")).isEqualTo(0);
    }

    /**
     * COLL-01: Duplicate collection name for the same user returns 409.
     */
    @Test
    @SuppressWarnings("unchecked")
    void createCollection_duplicateName_returns409() {
        Map<String, Object> body = Map.of("name", "MyList", "isPublic", false);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, bearerHeaders(aliceToken));

        ResponseEntity<Map> first = restTemplate.exchange(
                "/api/collections", HttpMethod.POST, req, Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> second = restTemplate.exchange(
                "/api/collections", HttpMethod.POST, req, Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * COLL-02: GET /api/collections returns the user's owned collections.
     */
    @Test
    @SuppressWarnings("unchecked")
    void getMyCollections_returnsOwnedList() {
        createCollectionAndGetId("ListA", false, aliceToken);
        createCollectionAndGetId("ListB", false, aliceToken);

        ResponseEntity<List> resp = restTemplate.exchange(
                "/api/collections", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(aliceToken)), List.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSizeGreaterThanOrEqualTo(2);
    }

    /**
     * COLL-03: Adding a book and then GET detail shows the olKey.
     */
    @Test
    @SuppressWarnings("unchecked")
    void addBook_thenGetDetail_returnsOlKey() {
        String collId = createCollectionAndGetId("ReadLater", false, aliceToken);

        // Add book
        Map<String, Object> bookBody = Map.of("olKey", "OL82586W");
        ResponseEntity<Void> addResp = restTemplate.exchange(
                "/api/collections/" + collId + "/books", HttpMethod.POST,
                new HttpEntity<>(bookBody, bearerHeaders(aliceToken)), Void.class);
        assertThat(addResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Get detail
        ResponseEntity<Map> detailResp = restTemplate.exchange(
                "/api/collections/" + collId, HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(aliceToken)), Map.class);
        assertThat(detailResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<String> olKeys = (List<String>) detailResp.getBody().get("olKeys");
        assertThat(olKeys).contains("OL82586W");
    }

    /**
     * COLL-04: Remove book, then detail shows empty olKeys.
     */
    @Test
    @SuppressWarnings("unchecked")
    void removeBook_thenGetDetail_olKeyGone() {
        String collId = createCollectionAndGetId("ToRemove", false, aliceToken);

        // Add then remove
        Map<String, Object> bookBody = Map.of("olKey", "OL12345W");
        restTemplate.exchange("/api/collections/" + collId + "/books", HttpMethod.POST,
                new HttpEntity<>(bookBody, bearerHeaders(aliceToken)), Void.class);
        restTemplate.exchange("/api/collections/" + collId + "/books/OL12345W",
                HttpMethod.DELETE, new HttpEntity<>(bearerHeaders(aliceToken)), Void.class);

        ResponseEntity<Map> detailResp = restTemplate.exchange(
                "/api/collections/" + collId, HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(aliceToken)), Map.class);
        assertThat(detailResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> olKeys = (List<?>) detailResp.getBody().get("olKeys");
        assertThat(olKeys).isEmpty();
    }

    /**
     * COLL-02: PATCH /api/collections/{id} updates the name.
     */
    @Test
    @SuppressWarnings("unchecked")
    void updateCollection_name_succeeds() {
        String collId = createCollectionAndGetId("OldName", false, aliceToken);

        Map<String, Object> patchBody = Map.of("name", "NewName");
        ResponseEntity<Map> patchResp = restTemplate.exchange(
                "/api/collections/" + collId, HttpMethod.PATCH,
                new HttpEntity<>(patchBody, bearerHeaders(aliceToken)), Map.class);

        assertThat(patchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchResp.getBody().get("name")).isEqualTo("NewName");
    }

    /**
     * COLL-01/02/03/04: Bob cannot PATCH Alice's collection — 403.
     */
    @Test
    @SuppressWarnings("unchecked")
    void ownershipGuard_403() {
        String collId = createCollectionAndGetId("AlicePrivate", false, aliceToken);

        Map<String, Object> patchBody = Map.of("name", "HackedName");
        ResponseEntity<Map> bobResp = restTemplate.exchange(
                "/api/collections/" + collId, HttpMethod.PATCH,
                new HttpEntity<>(patchBody, bearerHeaders(bobToken)), Map.class);

        assertThat(bobResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * COLL-01: DELETE collection cascades, GET list becomes empty.
     */
    @Test
    @SuppressWarnings("unchecked")
    void deleteCollection_cascades() {
        String collId = createCollectionAndGetId("ToDelete", false, aliceToken);
        // Add a book first to verify cascade
        restTemplate.exchange("/api/collections/" + collId + "/books", HttpMethod.POST,
                new HttpEntity<>(Map.of("olKey", "OL99999W"), bearerHeaders(aliceToken)), Void.class);

        ResponseEntity<Void> deleteResp = restTemplate.exchange(
                "/api/collections/" + collId, HttpMethod.DELETE,
                new HttpEntity<>(bearerHeaders(aliceToken)), Void.class);
        assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // List should not contain the deleted collection
        ResponseEntity<List> listResp = restTemplate.exchange(
                "/api/collections", HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(aliceToken)), List.class);
        List<?> collections = listResp.getBody();
        boolean found = collections != null && collections.stream()
                .anyMatch(item -> collId.equals(((Map<?, ?>) item).get("id")));
        assertThat(found).isFalse();
    }

    /**
     * COLL-03: Private collection visible to owner, 403 to other user.
     */
    @Test
    @SuppressWarnings("unchecked")
    void privateCollection_visibleToOwner_forbiddenToOther() {
        String collId = createCollectionAndGetId("PrivateList", false, aliceToken);

        // Bob (not owner) gets 403
        ResponseEntity<Map> bobResp = restTemplate.exchange(
                "/api/collections/" + collId, HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(bobToken)), Map.class);
        assertThat(bobResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Alice (owner) gets 200
        ResponseEntity<Map> aliceResp = restTemplate.exchange(
                "/api/collections/" + collId, HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(aliceToken)), Map.class);
        assertThat(aliceResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ----------------------------------------------------------------
    // Private helper
    // ----------------------------------------------------------------

    private record UserInfo(String token) {}
}
