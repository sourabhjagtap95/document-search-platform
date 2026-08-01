package com.docsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed, immutable binding for the {@code app.*} configuration block.
 *
 * <p>Constructor binding on a record means missing or misspelled properties fail
 * at startup rather than surfacing as nulls later. {@code version} is filled in by
 * Maven resource filtering from the project version.
 */
@ConfigurationProperties(prefix = "app")
public record ApplicationProperties(String name, String version, String description) {
}
