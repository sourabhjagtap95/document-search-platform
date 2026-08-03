package com.docsearch.infrastructure.opensearch;

import com.docsearch.config.OpenSearchProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.stream.JsonParser;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.GetMappingResponse;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Creates the documents index from {@code opensearch/documents-index.json}, which
 * holds the analysis settings and the explicit field mappings.
 *
 * <p>The definition lives in a resource file rather than in Java builders on purpose:
 * it is the same JSON you would paste into Dev Tools, so it can be tried by hand
 * before being committed, and it reads as one artefact instead of a chain of lambdas.
 *
 * <p>If the index already exists this only reports drift. Field mappings are largely
 * immutable once created — you can add a field, but you cannot change an existing
 * field's type or analyzer, because the terms already written to disk were produced
 * by the old analysis chain. Moving an existing index onto a new mapping means
 * reindexing into a fresh one, which is Day 8's subject.
 */
@Component
@ConditionalOnProperty(name = "opensearch.auto-create-index", matchIfMissing = true)
public class DocumentIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexInitializer.class);
    private static final String DEFINITION = "opensearch/documents-index.json";

    /** Field name to expected mapping type, used only for the drift report. */
    private static final Map<String, String> EXPECTED_TYPES = Map.of(
            "id", "keyword",
            "title", "text",
            "content", "text",
            "author", "keyword",
            "category", "keyword",
            "tags", "keyword",
            "createdAt", "date",
            "updatedAt", "date");

    private final OpenSearchClient client;
    private final ObjectMapper json;
    private final String index;

    public DocumentIndexInitializer(OpenSearchClient client,
                                    ObjectMapper openSearchObjectMapper,
                                    OpenSearchProperties properties) {
        this.client = client;
        this.json = openSearchObjectMapper;
        this.index = properties.documentsIndex();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createIndexIfMissing() throws IOException {
        if (client.indices().exists(request -> request.index(index)).value()) {
            reportDrift();
            return;
        }

        JsonNode definition;
        try (InputStream in = new ClassPathResource(DEFINITION).getInputStream()) {
            definition = json.readTree(in);
        }

        // Deserialized into the client's own types rather than posted as raw JSON, so a
        // typo in the definition fails here against the typed model instead of being
        // shipped to the cluster and rejected with a less obvious error.
        IndexSettings settings = deserialize(definition.path("settings"), IndexSettings._DESERIALIZER);
        TypeMapping mappings = deserialize(definition.path("mappings"), TypeMapping._DESERIALIZER);

        client.indices().create(request -> request
                .index(index)
                .settings(settings)
                .mappings(mappings));

        log.info("Created index '{}' from {} — explicit mappings, custom analyzer, dynamic=strict",
                index, DEFINITION);
    }

    private <T> T deserialize(JsonNode node, JsonpDeserializer<T> deserializer) {
        JsonpMapper mapper = client._transport().jsonpMapper();
        try (JsonParser parser = mapper.jsonProvider()
                .createParser(new java.io.StringReader(node.toString()))) {
            return deserializer.deserialize(parser, mapper);
        }
    }

    /**
     * Compares the live mapping against what {@link #DEFINITION} declares and logs the
     * differences with the command to fix them. Deliberately advisory: silently
     * mutating an index that already holds data is never the right default.
     */
    private void reportDrift() throws IOException {
        GetMappingResponse response = client.indices().getMapping(request -> request.index(index));
        var mappings = response.result().get(index);
        if (mappings == null || mappings.mappings() == null) {
            log.info("Index '{}' exists; mapping could not be read for comparison", index);
            return;
        }

        Map<String, String> live = new TreeMap<>();
        mappings.mappings().properties()
                .forEach((field, property) -> live.put(field, property._kind().jsonValue()));

        Map<String, String> drift = new LinkedHashMap<>();
        EXPECTED_TYPES.forEach((field, expected) -> {
            String actual = live.get(field);
            if (!expected.equals(actual)) {
                drift.put(field, "expected " + expected + ", found " + (actual == null ? "absent" : actual));
            }
        });

        if (drift.isEmpty()) {
            log.info("Index '{}' exists and its field types match {}", index, DEFINITION);
            return;
        }

        log.warn("Index '{}' does not match {} — {} field(s) differ: {}", index, DEFINITION, drift.size(), drift);
        log.warn("Field types and analyzers cannot be changed in place. In development, recreate the index:");
        log.warn("    curl -X DELETE 'http://localhost:9200/{}'   then restart", index);
        log.warn("Day 8 replaces this with a proper reindex so production data is not lost.");
    }
}
