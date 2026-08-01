package com.docsearch.api;

import com.docsearch.api.dto.HealthResponse;
import com.docsearch.config.ApplicationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final ApplicationProperties properties;

    public HealthController(ApplicationProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return HealthResponse.up(properties.name(), properties.version(), jvmUptime());
    }

    private Duration jvmUptime() {
        return Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
    }
}
