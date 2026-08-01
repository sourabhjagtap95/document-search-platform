package com.docsearch.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.Instant;

/**
 * Lightweight liveness payload for {@code GET /api/v1/health}.
 *
 * <p>Deliberately separate from {@code /actuator/health}: actuator reports on
 * infrastructure the app depends on, while this endpoint answers only
 * "is the application itself serving requests, and which build is it?".
 */
@Schema(name = "HealthResponse", description = "Application liveness snapshot")
public record HealthResponse(

        @Schema(description = "Always UP when the application is serving requests.",
                example = "UP")
        String status,

        @Schema(description = "Configured application name.",
                example = "Document Search Platform")
        String application,

        @Schema(description = "Build version, supplied by Maven resource filtering.",
                example = "0.1.0-SNAPSHOT")
        String version,

        @Schema(description = "JVM uptime as an ISO-8601 duration.",
                example = "PT2.136S")
        String uptime,

        @Schema(description = "Server time the response was produced, in UTC.",
                example = "2026-08-01T08:10:20.397797310Z")
        Instant timestamp
) {

    public static HealthResponse up(String application, String version, Duration uptime) {
        return new HealthResponse("UP", application, version, uptime.toString(), Instant.now());
    }
}
