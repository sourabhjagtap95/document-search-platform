package com.docsearch.infrastructure;

import com.docsearch.domain.SearchDocument;
import com.docsearch.infrastructure.mongo.DocumentEntity;
import com.docsearch.infrastructure.mongo.DocumentRepository;
import com.docsearch.infrastructure.opensearch.OpenSearchDocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Seeds {@code sample-documents.json} on startup so a fresh checkout has something to
 * work with. Disable with {@code app.sample-data.enabled=false}.
 *
 * <p>MongoDB is the source of truth, so its emptiness decides whether to seed at all —
 * that keeps restarts idempotent and preserves hand-made edits.
 *
 * <p>It then writes the same documents straight into the search index. That is a
 * <strong>dual write</strong>, and deliberately the naive version: two independent writes
 * with nothing tying them together. Day 5 replaces it with real synchronisation, and
 * shows how this shape drifts the moment one of the two fails.
 */
@Component
@ConditionalOnProperty(name = "app.sample-data.enabled", matchIfMissing = true)
@Order(Ordered.LOWEST_PRECEDENCE)   // after DocumentIndexInitializer
public class SampleDataLoader {

    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);
    private static final String RESOURCE = "sample-documents.json";

    private final DocumentRepository mongo;
    private final OpenSearchDocumentRepository index;
    private final ObjectMapper mapper;

    public SampleDataLoader(DocumentRepository mongo,
                            OpenSearchDocumentRepository index,
                            ObjectMapper openSearchObjectMapper) {
        this.mongo = mongo;
        this.index = index;
        this.mapper = openSearchObjectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() throws IOException {
        long stored = mongo.count();
        if (stored > 0) {
            log.info("MongoDB already holds {} documents — skipping sample data", stored);
            return;
        }

        List<SearchDocument> documents = readSampleDocuments();

        mongo.saveAll(documents.stream().map(DocumentEntity::fromDomain).toList());
        log.info("Seeded {} sample documents into MongoDB", documents.size());

        // A failure to index must not lose documents already persisted to the source of
        // truth, so this is reported rather than propagated.
        try {
            index.saveAll(documents);
            log.info("Indexed {} sample documents into OpenSearch", documents.size());
        } catch (IOException | RuntimeException failure) {
            log.warn("Sample documents were persisted but could not be indexed — the index "
                    + "is now behind MongoDB. This is the drift Day 5 addresses.", failure);
        }
    }

    private List<SearchDocument> readSampleDocuments() throws IOException {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            Instant now = Instant.now();
            List<SampleDocument> raw = mapper.readValue(in, mapper.getTypeFactory()
                    .constructCollectionType(List.class, SampleDocument.class));

            return raw.stream()
                    .map(sample -> new SearchDocument(
                            UUID.randomUUID().toString(),
                            sample.title(),
                            sample.content(),
                            sample.author(),
                            sample.category(),
                            sample.tags(),
                            now,
                            now))
                    .toList();
        }
    }

    /** Shape of an entry in {@code sample-documents.json} — no ids or timestamps. */
    private record SampleDocument(String title, String content, String author,
                                  String category, List<String> tags) {
    }
}
