package com.docsearch.api.dto;

import java.time.Duration;
import java.time.Instant;

/**
 * Lightweight liveness payload for {@code GET /api/v1/health}.
 *
 * <p>Deliberately separate from {@code /actuator/health}: actuator reports on
 * infrastructure the app depends on, while this endpoint answers only
 * "is the application itself serving requests, and which build is it?".
 */
public record HealthResponse(
        String status,
        String application,
        String version,
        String uptime,
        Instant timestamp
) {

    public static HealthResponse up(String application, String version, Duration uptime) {
        return new HealthResponse("UP", application, version, uptime.toString(), Instant.now());
    }
}
