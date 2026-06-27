package com.booktracker;

import com.booktracker.books.BookEntity;
import com.booktracker.books.BookRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OPS-06: End-to-end reading flow integration test.
 *
 * <p>Chains the full user journey against a real PostgreSQL 16 container:
 * <ol>
 *   <li>Register — 201 Created, body contains a non-blank JWT</li>
 *   <li>Login — 200 OK, body contains a non-blank JWT</li>
 *   <li>Seed a {@link BookEntity} via {@link BookRepository} (avoids Open Library network call)</li>
 *   <li>Add to shelf — 201 Created, body contains a non-blank {@code entryId}</li>
 *   <li>List shelf — 200 OK, content has size 1 and the entry title is "Flow Test Book"</li>
 *   <li>Update progress — 200 OK, body {@code currentPage} == 150</li>
 *   <li>Get stats — 200 OK, {@code currentlyReadingCount} == 1, {@code booksPerMonth} has 12 elements</li>
 * </ol>
 *
 * <p>This test uses the same {@code @SpringBootTest + @Testcontainers + @DynamicPropertySource}
 * scaffold as all other integration tests in this project. URL paths use the
 * {@code /api/...} prefix introduced by the Task 1 context-path refactor.
 *
 * <p>Authorization scenario (user A accessing user B's entries) is already covered
 * by {@code ShelfIntegrationTest.getEntryOwnershipReturns403()} — this test does
 * not duplicate it (D-10).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReadingFlowIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("booktracker_flow_test")
                    .withUsername("bt_flow")
                    .withPassword("bt_flow_pw");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled",      () -> "true");
        registry.add("jwt.secret",
                () -> "dGVzdC1zZWNyZXQtYmFzZTY0LWVuY29kZWQtMzJieXRlcw==");
        registry.add("openlibrary.validate-base-url", () -> "false");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BookRepository bookRepository;

    @Test
    @SuppressWarnings("unchecked")
    void fullReadingFlow_registerLoginAddListUpdateProgressStats() {

        // ── 1. Register ──────────────────────────────────────────────────────
        Map<String, Object> registerBody = Map.of(
            "email",       "flow@example.com",
            "password",    "securepassword123",
            "displayName", "FlowUser"
        );
        ResponseEntity<Map> registerResp = restTemplate.postForEntity(
            "/api/auth/register", registerBody, Map.class);
        assertThat(registerResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResp.getBody().get("token").toString()).isNotBlank();

        // ── 2. Login (verify credentials round-trip) ──────────────────────────
        Map<String, Object> loginBody = Map.of(
            "email",    "flow@example.com",
            "password", "securepassword123"
        );
        ResponseEntity<Map> loginResp = restTemplate.postForEntity(
            "/api/auth/login", loginBody, Map.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = loginResp.getBody().get("token").toString();
        assertThat(token).isNotBlank();

        // ── 3. Seed book via repository (avoids Open Library network call) ─────
        BookEntity book = new BookEntity();
        book.setOpenLibraryKey("/works/OLflowW");
        book.setTitle("Flow Test Book");
        book.setAuthors("Flow Author");
        book.setPageCount(300);
        bookRepository.save(book);

        // ── 4. Add to shelf ───────────────────────────────────────────────────
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // Short-form key "OLflowW" — ShelfService resolves it to "/works/OLflowW".
        Map<String, Object> addBody = Map.of(
            "olKey",  "OLflowW",
            "status", "CURRENTLY_READING"
        );
        ResponseEntity<Map> addResp = restTemplate.exchange(
            "/api/shelf", HttpMethod.POST,
            new HttpEntity<>(addBody, headers), Map.class);
        assertThat(addResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String entryId = addResp.getBody().get("entryId").toString();
        assertThat(entryId).isNotBlank();

        // ── 5. List shelf ──────────────────────────────────────────────────────
        ResponseEntity<Map> listResp = restTemplate.exchange(
            "/api/shelf", HttpMethod.GET,
            new HttpEntity<>(headers), Map.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) listResp.getBody().get("content");
        assertThat(content).hasSize(1);
        Map<?, ?> entry = (Map<?, ?>) content.get(0);
        assertThat(entry.get("title")).isEqualTo("Flow Test Book");

        // ── 6. Update reading progress ────────────────────────────────────────
        Map<String, Object> progressBody = Map.of("currentPage", 150);
        ResponseEntity<Map> progressResp = restTemplate.exchange(
            "/api/shelf/" + entryId + "/progress", HttpMethod.PATCH,
            new HttpEntity<>(progressBody, headers), Map.class);
        assertThat(progressResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(progressResp.getBody().get("currentPage")).isEqualTo(150);

        // ── 7. Get stats ───────────────────────────────────────────────────────
        ResponseEntity<Map> statsResp = restTemplate.exchange(
            "/api/stats", HttpMethod.GET,
            new HttpEntity<>(headers), Map.class);
        assertThat(statsResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> stats = statsResp.getBody();
        assertThat(stats.get("currentlyReadingCount")).isEqualTo(1);
        assertThat(stats.get("booksPerMonth")).isInstanceOf(List.class);
        assertThat((List<?>) stats.get("booksPerMonth")).hasSize(12);
    }
}
