package com.docsearch.application;

import com.docsearch.domain.SearchDocument;
import com.docsearch.infrastructure.mongo.DocumentEntity;
import com.docsearch.infrastructure.mongo.DocumentRepository;
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
 * Seeds {@code sample-documents.json} on startup so a fresh checkout has something to work
 * with. Disable with {@code app.sample-data.enabled=false}.
 *
 * <p>MongoDB is the source of truth, so its emptiness decides whether to seed — that keeps
 * restarts idempotent and preserves hand-made edits.
 *
 * <p>Up to Day 4 this wrote to MongoDB and then straight into OpenSearch: two independent
 * writes with nothing tying them together, and a hand-rolled warning when the second failed.
 * It now hands the documents to {@link DocumentIndexingService}, which projects them into
 * every configured index and reports per-index failures the same way the request path does.
 * One place decides what a partial failure means.
 *
 * <p>That change is also why this class moved out of {@code infrastructure}. It now
 * orchestrates a write to the source of truth followed by a projection into the indexes —
 * the same job {@link DocumentService#persist} does for a request — and {@code application}
 * is where that belongs. Left in {@code infrastructure} it would depend on
 * {@code application} while {@code application} depends on {@code infrastructure.mongo},
 * and {@code ArchitectureRulesTest}'s cycle check would fail the build.
 */
@Component
@ConditionalOnProperty(name = "app.sample-data.enabled", matchIfMissing = true)
@Order(Ordered.LOWEST_PRECEDENCE)   // after the index and collection initializers
public class SampleDataLoader {

    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);
    private static final String RESOURCE = "sample-documents.json";

    private final DocumentRepository mongo;
    private final DocumentIndexingService indexing;
    private final ObjectMapper mapper;

    public SampleDataLoader(DocumentRepository mongo,
                            DocumentIndexingService indexing,
                            ObjectMapper openSearchObjectMapper) {
        this.mongo = mongo;
        this.indexing = indexing;
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

        documents.forEach(document -> {
            List<String> failed = indexing.index(document);
            if (!failed.isEmpty()) {
                log.warn("Sample document {} was persisted but not indexed in {}",
                        document.id(), failed);
            }
        });
        log.info("Projected sample documents into: {}", indexing.describe());
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
