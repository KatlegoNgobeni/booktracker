package com.booktracker.health;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check endpoint — returns {"status":"ok"} with HTTP 200.
 *
 * Combined with the /api context-path in application.properties, this is
 * served at GET /api/health. The response contains only the literal status
 * string; no version, build info, or environment detail is exposed.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
