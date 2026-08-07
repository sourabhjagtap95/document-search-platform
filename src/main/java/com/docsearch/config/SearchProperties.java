package com.docsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param backend name of the {@code DocumentSearchPort} that answers searches when the request
 *                does not name one. Must match a port's {@code name()}.
 */
@ConfigurationProperties(prefix = "search")
public record SearchProperties(String backend) {
}
