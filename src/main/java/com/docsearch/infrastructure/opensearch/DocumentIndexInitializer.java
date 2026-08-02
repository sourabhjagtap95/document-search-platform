package com.docsearch.infrastructure.opensearch;

import com.docsearch.config.OpenSearchProperties;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Creates the documents index at startup when it is missing.
 *
 * <p>The index is created with dynamic mapping — OpenSearch infers field types on
 * first write. That is deliberate for Day 2: explicit mappings, analyzers and the
 * text-vs-keyword decision are Day 3's subject, and seeing what dynamic mapping
 * guesses first makes those choices concrete.
 */
@Component
@ConditionalOnProperty(name = "opensearch.auto-create-index", matchIfMissing = true)
public class DocumentIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexInitializer.class);

    private final OpenSearchClient client;
    private final String index;

    public DocumentIndexInitializer(OpenSearchClient client, OpenSearchProperties properties) {
        this.client = client;
        this.index = properties.documentsIndex();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createIndexIfMissing() throws IOException {
        if (client.indices().exists(request -> request.index(index)).value()) {
            log.info("OpenSearch index '{}' already exists", index);
            return;
        }

        client.indices().create(request -> request
                .index(index)
                .settings(settings -> settings
                        .numberOfShards(1)
                        // Zero replicas keeps a single-node cluster green; a replica
                        // would have nowhere to live and leave the index yellow.
                        // Day 8 covers shards and replicas properly.
                        .numberOfReplicas(0)));

        log.info("Created OpenSearch index '{}' (1 shard, 0 replicas, dynamic mapping)", index);
    }
}
