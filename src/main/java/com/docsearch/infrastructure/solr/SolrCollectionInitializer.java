package com.docsearch.infrastructure.solr;

import com.docsearch.config.SolrProperties;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.response.schema.SchemaResponse;
import org.apache.solr.common.SolrException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates the Solr collection and declares its schema when missing.
 *
 * <p>The parallel with Day 3 is the point. Solr's {@code _default} configset is
 * "schemaless": it guesses a field's type from the first value it sees, exactly as
 * OpenSearch's dynamic mapping did, and with the same consequences — a field typed by
 * accident, and no error when the guess is wrong. So the fields are declared here for the
 * same reasons they are declared in {@code documents-index.json}.
 *
 * <p>The type choices mirror the OpenSearch mapping: {@code text_general} where prose is
 * searched, {@code string} where a value is filtered or faceted on. Solr calls the second
 * one {@code string} rather than {@code keyword}, but it is the same idea — stored verbatim,
 * not broken into words.
 */
@Component
// Both names must match, which is what @ConditionalOnProperty does with a list. Gating only
// on auto-create-collection left this bean asking for a SolrClient that solr.enabled=false
// had already removed, so turning Solr off broke the context instead of shrinking it.
@ConditionalOnProperty(
        name = {"solr.enabled", "solr.auto-create-collection"}, matchIfMissing = true)
public class SolrCollectionInitializer {

    private static final Logger log = LoggerFactory.getLogger(SolrCollectionInitializer.class);

    /** Field name to Solr field type. Compare with {@code documents-index.json}. */
    private static final Map<String, String> FIELDS = new LinkedHashMap<>();

    static {
        FIELDS.put("title", "text_general");     // prose, analyzed
        FIELDS.put("content", "text_general");   // prose, analyzed
        FIELDS.put("author", "string");          // exact: grouped and filtered
        FIELDS.put("category", "string");        // exact: filtered and faceted
        FIELDS.put("createdAt", "pdate");        // ranges and sorting
        FIELDS.put("updatedAt", "pdate");
    }

    /** Multi-valued, so declared separately from the single-valued fields above. */
    private static final String TAGS_FIELD = "tags";

    private final SolrClient client;
    private final SolrProperties properties;

    public SolrCollectionInitializer(SolrClient client, SolrProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void createCollectionIfMissing() {
        String collection = properties.collection();
        try {
            if (collectionExists(collection)) {
                log.info("Solr collection '{}' already exists", collection);
            } else {
                CollectionAdminRequest
                        .createCollection(collection, properties.shards(), properties.replicationFactor())
                        .process(client);
                log.info("Created Solr collection '{}' ({} shard(s), replication factor {})",
                        collection, properties.shards(), properties.replicationFactor());
            }
            declareSchema(collection);
        } catch (SolrServerException | IOException | SolrException failure) {
            // Solr is a derived index, not the source of truth, so an unreachable Solr must
            // not stop the application from serving MongoDB-backed requests.
            //
            // SolrException is in that list because it is the one that actually happens.
            // A dead ZooKeeper ensemble surfaces as a SolrException wrapping a
            // TimeoutException — a RuntimeException, so catching only the checked pair let
            // it escape this listener and abort startup, which is the opposite of what the
            // paragraph above promises.
            log.warn("Could not prepare Solr collection '{}' — Solr indexing will fail until "
                    + "this is resolved. MongoDB-backed endpoints are unaffected.", collection, failure);
        }
    }

    private boolean collectionExists(String collection) throws SolrServerException, IOException {
        List<String> existing = CollectionAdminRequest.listCollections(client);
        return existing != null && existing.contains(collection);
    }

    /**
     * Adds any declared field the schema does not already have. Additive on purpose: Solr
     * can add a field to a live collection, but changing an existing field's type has the
     * same problem as in OpenSearch — the terms already written came from the old analysis
     * — so a type change needs a rebuild rather than an edit.
     */
    private void declareSchema(String collection) throws SolrServerException, IOException {
        SchemaResponse.FieldsResponse response = new SchemaRequest.Fields().process(client, collection);
        Set<String> present = response.getFields().stream()
                .map(field -> String.valueOf(field.get("name")))
                .collect(Collectors.toSet());

        int added = 0;
        for (Map.Entry<String, String> field : FIELDS.entrySet()) {
            if (present.contains(field.getKey())) {
                continue;
            }
            new SchemaRequest.AddField(Map.of(
                    "name", field.getKey(),
                    "type", field.getValue(),
                    "indexed", true,
                    "stored", true,
                    "multiValued", false)).process(client, collection);
            added++;
        }

        if (!present.contains(TAGS_FIELD)) {
            new SchemaRequest.AddField(Map.of(
                    "name", TAGS_FIELD,
                    "type", "string",
                    "indexed", true,
                    "stored", true,
                    "multiValued", true)).process(client, collection);
            added++;
        }

        if (added > 0) {
            log.info("Declared {} field(s) on Solr collection '{}'", added, collection);
        } else {
            log.info("Solr collection '{}' schema already declares every field", collection);
        }
    }
}
