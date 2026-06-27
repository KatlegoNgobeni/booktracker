package com.booktracker.health;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check endpoint — returns the application and database connectivity status.
 *
 * <ul>
 *   <li>HTTP 200 + {@code {"status":"ok"}} when the database connection probe succeeds.</li>
 *   <li>HTTP 503 + {@code {"status":"down"}} when the probe fails.</li>
 * </ul>
 *
 * The response body never includes version, build info, exception text, or the JDBC URL
 * (mitigates T-01-08: Information Disclosure via down response).
 *
 * <p>The {@link DbHealthIndicator} dependency is marked optional so that
 * {@code @WebMvcTest} slices — which do not load the full application context and
 * therefore cannot wire a {@code DataSource} — can still load this controller without
 * error.  In production the full context always provides a {@code DataSource} and the
 * indicator is never {@code null}.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final DbHealthIndicator dbHealthIndicator;

    public HealthController(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            DbHealthIndicator dbHealthIndicator) {
        this.dbHealthIndicator = dbHealthIndicator;
    }

    /**
     * GET /api/health
     *
     * @return 200 {"status":"ok"} when the DB is reachable; 503 {"status":"down"} otherwise.
     */
    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        boolean dbUp = (dbHealthIndicator != null) && dbHealthIndicator.isDbReachable();
        if (dbUp) {
            return ResponseEntity.ok(Map.of("status", "ok"));
        }
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "down"));
    }
}
