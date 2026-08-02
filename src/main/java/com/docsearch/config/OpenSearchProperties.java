package com.docsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param uri              base URI of the OpenSearch cluster
 * @param documentsIndex   index holding {@code SearchDocument}s
 * @param autoCreateIndex  create the index at startup when it does not exist.
 *                         Turned off in tests so the context loads without a
 *                         live cluster.
 */
@ConfigurationProperties(prefix = "opensearch")
public record OpenSearchProperties(String uri, String documentsIndex, boolean autoCreateIndex) {
}
