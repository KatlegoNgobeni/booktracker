package com.booktracker.activity;

import com.booktracker.user.UserEntity;
import com.booktracker.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ReadingActivityRepository} against a real
 * PostgreSQL 16 database (Testcontainers).
 *
 * <p>Why Testcontainers and not H2: {@code ON CONFLICT DO NOTHING} is
 * PostgreSQL syntax — H2 is banned per CLAUDE.md.
 *
 * <p>Test methods are {@code @Transactional}: the {@code @Modifying} native
 * insert requires an active transaction (RESEARCH Pitfall 5), and rollback
 * gives per-test isolation.
 */
@SpringBootTest
@Testcontainers
class ReadingActivityRepositoryTest {

    /** Shared PostgreSQL 16 container — started once for the class. */
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("booktracker_test")
                    .withUsername("bt_test")
                    .withPassword("bt_test_pw");

    /**
     * Wire the Testcontainers JDBC URL/credentials into the Spring
     * datasource properties so Flyway and Hibernate talk to the container.
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Flyway must be enabled so V7 creates reading_activity
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private ReadingActivityRepository readingActivityRepository;

    @Autowired
    private UserRepository userRepository;

    /** Persists and flushes a user so the reading_activity FK is satisfiable. */
    private UUID persistUser(String email) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash("$2a$10$test-hash-not-a-real-password-hash");
        user.setDisplayName("Test Reader");
        return userRepository.saveAndFlush(user).getId();
    }

    /**
     * STATS-03 (idempotency): recording the same (user, date) activity twice
     * does not error and produces exactly one row.
     */
    @Test
    @Transactional
    void recordActivity_calledTwiceForSameUserAndDate_leavesExactlyOneRow() {
        UUID userId = persistUser("streak-idempotent@test.com");
        LocalDate today = LocalDate.of(2026, 7, 9);

        readingActivityRepository.recordActivity(userId, today);
        readingActivityRepository.recordActivity(userId, today);

        List<LocalDate> dates = readingActivityRepository.findActivityDatesDesc(userId);
        assertThat(dates)
                .as("double-insert of the same (user, date) must produce exactly one row")
                .containsExactly(today);
    }

    /**
     * STATS-01/02 (query shape): activity dates are returned distinct and
     * sorted newest-first regardless of insertion order.
     */
    @Test
    @Transactional
    void findActivityDatesDesc_datesRecordedOutOfOrder_returnsNewestFirst() {
        UUID userId = persistUser("streak-ordering@test.com");

        readingActivityRepository.recordActivity(userId, LocalDate.of(2026, 7, 1));
        readingActivityRepository.recordActivity(userId, LocalDate.of(2026, 7, 3));
        readingActivityRepository.recordActivity(userId, LocalDate.of(2026, 7, 2));

        List<LocalDate> dates = readingActivityRepository.findActivityDatesDesc(userId);
        assertThat(dates)
                .as("activity dates must be ordered activity_date DESC")
                .containsExactly(
                        LocalDate.of(2026, 7, 3),
                        LocalDate.of(2026, 7, 2),
                        LocalDate.of(2026, 7, 1));
    }

    /**
     * STATS-01/02 (isolation): activity recorded for user A does not appear
     * in user B's activity dates.
     */
    @Test
    @Transactional
    void findActivityDatesDesc_activityRecordedForOtherUser_isNotReturned() {
        UUID userA = persistUser("streak-user-a@test.com");
        UUID userB = persistUser("streak-user-b@test.com");

        readingActivityRepository.recordActivity(userA, LocalDate.of(2026, 7, 8));
        readingActivityRepository.recordActivity(userB, LocalDate.of(2026, 7, 9));

        List<LocalDate> datesForB = readingActivityRepository.findActivityDatesDesc(userB);
        assertThat(datesForB)
                .as("user B must only see their own activity dates")
                .containsExactly(LocalDate.of(2026, 7, 9));
    }
}
