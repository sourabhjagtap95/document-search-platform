package com.docsearch.api;

import com.docsearch.api.dto.HealthResponse;
import com.docsearch.config.ApplicationProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health", description = "Application liveness")
public class HealthController {

    private final ApplicationProperties properties;

    public HealthController(ApplicationProperties properties) {
        this.properties = properties;
    }

    @Operation(
            summary = "Application liveness",
            description = """
                    Reports whether the application itself is serving requests, and which
                    build is running.

                    This does not check MongoDB or OpenSearch — use /actuator/health for
                    dependency status.""")
    @ApiResponse(
            responseCode = "200",
            description = "The application is serving requests",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = HealthResponse.class)))
    @GetMapping("/health")
    public HealthResponse health() {
        return HealthResponse.up(properties.name(), properties.version(), jvmUptime());
    }

    private Duration jvmUptime() {
        return Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
    }
}
