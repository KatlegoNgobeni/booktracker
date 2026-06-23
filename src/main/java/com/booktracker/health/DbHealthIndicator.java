package com.booktracker.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Probes the database connection by acquiring a JDBC Connection and calling
 * {@code isValid(timeoutSeconds)}.  The timeout bounds the probe so an
 * unresponsive database cannot stall the health endpoint indefinitely
 * (T-01-07: DoS accept — probe is read-only and bounded).
 *
 * <p>Returns {@code true} only when the JDBC driver confirms the connection
 * is live within the timeout window.  Any {@link SQLException} results in
 * {@code false} — the exception message is logged server-side but never
 * forwarded to the HTTP client (T-01-08: no JDBC URL/trace leakage).
 */
@Component
public class DbHealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(DbHealthIndicator.class);

    /** Seconds the JDBC driver may use to validate the connection. */
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public DbHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Returns {@code true} if the database is reachable and the connection
     * passes the JDBC {@code isValid()} check; {@code false} otherwise.
     */
    public boolean isDbReachable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException e) {
            // Log details server-side only — never expose to the HTTP client
            log.warn("Database health check failed: {}", e.getMessage());
            return false;
        }
    }
}
