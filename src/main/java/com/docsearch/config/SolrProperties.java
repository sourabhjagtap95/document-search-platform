package com.docsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param zkHost               ZooKeeper connect string, optionally with a chroot suffix
 *                             such as {@code localhost:2181/solr}
 * @param collection           collection holding documents
 * @param autoCreateCollection create the collection and its schema at startup when absent
 * @param shards               shard count used only when creating the collection
 * @param replicationFactor    replica count used only when creating the collection
 * @param connectTimeoutMs     how long to wait for ZooKeeper before giving up
 */
@ConfigurationProperties(prefix = "solr")
public record SolrProperties(
        boolean enabled,
        String zkHost,
        String collection,
        boolean autoCreateCollection,
        int shards,
        int replicationFactor,
        int connectTimeoutMs
) {
}
