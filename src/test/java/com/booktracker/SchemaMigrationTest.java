package com.booktracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: verifies that Flyway applies V1__initial_schema.sql correctly
 * against a real PostgreSQL 16 database (via Testcontainers).
 *
 * <p>Why Testcontainers and not H2: H2 does not support PostgreSQL-specific DDL
 * such as {@code gen_random_uuid()} and {@code timestamptz}.  This test provides
 * the same guarantee as production.
 *
 * <p>The {@code *Test} suffix ensures default Maven Surefire picks up this class
 * without any extra {@code includes} configuration.
 */
@SpringBootTest
@Testcontainers
class SchemaMigrationTest {

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
        // Flyway must be enabled for the migration to run
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private DataSource dataSource;

    /**
     * Asserts that the five tables defined in V1__initial_schema.sql are present
     * in the Testcontainers Postgres 16 instance after Flyway migration.
     */
    @Test
    void v1MigrationCreatesAllFiveTables() throws SQLException {
        List<String> foundTables = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(
                     null, "public", "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                foundTables.add(rs.getString("TABLE_NAME").toLowerCase());
            }
        }

        assertThat(foundTables)
                .as("V1 migration must create all five domain tables")
                .contains("users", "books", "user_books", "follows", "goals");
    }

    /**
     * Asserts that the shelf_status column in user_books is character varying
     * (stored as STRING, not integer ordinal) — matches EnumType.STRING strategy.
     */
    @Test
    void shelfStatusColumnIsVarcharNotInteger() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getColumns(
                     null, "public", "user_books", "shelf_status")) {
            assertThat(rs.next())
                    .as("shelf_status column must exist in user_books")
                    .isTrue();
            String typeName = rs.getString("TYPE_NAME").toLowerCase();
            assertThat(typeName)
                    .as("shelf_status must be stored as varchar/text, not integer")
                    .isIn("varchar", "character varying", "text");
        }
    }
}
