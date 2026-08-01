package com.docsearch.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Lightweight liveness payload for {@code GET /api/v1/health}.
 *
 * <p>Deliberately separate from {@code /actuator/health}: actuator reports on
 * infrastructure the app depends on, while this endpoint answers only
 * "is the application itself serving requests?".
 */
@Schema(name = "HealthResponse", description = "Application liveness snapshot")
public record HealthResponse(

        @Schema(description = "Always UP when the application is serving requests.",
                example = "UP")
        String status,

        @Schema(description = "Configured application name.",
                example = "document-search-platform")
        String application,

        @Schema(description = "Server time the response was produced, in UTC.",
                example = "2026-08-01T08:10:20.397797310Z")
        Instant timestamp
) {

    public static HealthResponse up(String application) {
        return new HealthResponse("UP", application, Instant.now());
    }
}
