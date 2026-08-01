package com.docsearch.api.dto;

import java.time.Instant;

/**
 * Lightweight liveness payload for {@code GET /api/v1/health}.
 *
 * <p>Deliberately separate from {@code /actuator/health}: actuator reports on
 * infrastructure the app depends on, while this endpoint answers only
 * "is the application itself serving requests?".
 */
public record HealthResponse(String status, String application, Instant timestamp) {

    public static HealthResponse up(String application) {
        return new HealthResponse("UP", application, Instant.now());
    }
}
