package com.docsearch.api;

import com.docsearch.api.dto.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health", description = "Application liveness")
public class HealthController {

    private final String applicationName;

    public HealthController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @Operation(
            summary = "Application liveness",
            description = """
                    Reports whether the application itself is serving requests.

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
        return HealthResponse.up(applicationName);
    }
}
