package com.docsearch.infrastructure.opensearch;

import com.docsearch.domain.SearchDocument;
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
 * Seeds {@code sample-documents.json} into the index on startup, but only when the
 * index is empty — so restarting does not pile up duplicates and hand-made edits
 * survive. Disable with {@code app.sample-data.enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "app.sample-data.enabled", matchIfMissing = true)
@Order(Ordered.LOWEST_PRECEDENCE)   // must run after DocumentIndexInitializer
public class SampleDataLoader {

    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);
    private static final String RESOURCE = "sample-documents.json";

    private final OpenSearchDocumentRepository repository;
    private final ObjectMapper mapper;

    public SampleDataLoader(OpenSearchDocumentRepository repository, ObjectMapper openSearchObjectMapper) {
        this.repository = repository;
        this.mapper = openSearchObjectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() throws IOException {
        long existing = repository.count();
        if (existing > 0) {
            log.info("Index already holds {} documents — skipping sample data", existing);
            return;
        }

        List<SearchDocument> seeded = readSampleDocuments();
        int indexed = repository.saveAll(seeded);
        log.info("Seeded {} sample documents", indexed);
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
