package com.docsearch.application;

import com.docsearch.domain.SearchDocument;
import com.docsearch.port.DocumentIndexPort;
import com.docsearch.port.IndexingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Keeps every search index in step with MongoDB.
 *
 * <p><strong>The failure model, which is the whole substance of this class.</strong>
 * MongoDB and the indexes have no shared transaction, so a write cannot be atomic across
 * them. Three positions are available and only one is defensible:
 *
 * <ul>
 *   <li><em>Index first, then persist</em> — a crash in between leaves a document that is
 *       searchable but does not exist. Unacceptable: search results would 404.
 *   <li><em>Persist, then index, and fail the request if indexing fails</em> — the caller
 *       gets an error for a write that actually succeeded, and will probably retry it,
 *       creating a duplicate.
 *   <li><em>Persist, then index, and report an indexing failure without failing the
 *       request</em> — the document exists and is briefly missing from search. Chosen.
 * </ul>
 *
 * <p>So the index is allowed to lag, and the system must be able to notice and repair
 * that: {@code /api/v1/admin/index-status} exposes the drift and
 * {@link ReindexService} rebuilds from the source of truth. That pairing — tolerate
 * divergence, make it visible, make it fixable — is what makes the chosen option safe,
 * and without it this would just be a dual write with better logging.
 *
 * <p>Each index is written independently, so Solr being down cannot stop OpenSearch being
 * updated.
 *
 * <p>Writes here are synchronous, which costs the caller a round trip per index. A queue
 * would remove that, at the price of a component that can itself fall behind; Day 9 is
 * where that trade-off is worth measuring.
 */
@Service
public class DocumentIndexingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexingService.class);

    private final List<DocumentIndexPort> indexes;

    public DocumentIndexingService(List<DocumentIndexPort> indexes) {
        this.indexes = List.copyOf(indexes);
        log.info("Document indexing active for: {}", names());
    }

    public List<String> names() {
        return indexes.stream().map(DocumentIndexPort::name).toList();
    }

    /** Projects one document into every index. Returns the names that failed. */
    public List<String> index(SearchDocument document) {
        return forEachIndex("index " + document.id(), port -> port.index(document));
    }

    /** Removes one document from every index. Returns the names that failed. */
    public List<String> delete(String id) {
        return forEachIndex("delete " + id, port -> port.delete(id));
    }

    /** Document counts per index, for comparison against MongoDB. */
    public Map<String, Long> counts() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (DocumentIndexPort port : indexes) {
            try {
                counts.put(port.name(), port.count());
            } catch (IndexingException failure) {
                log.warn("Could not count documents in {}", port.name(), failure);
                counts.put(port.name(), -1L);   // -1 distinguishes unreachable from empty
            }
        }
        return counts;
    }

    List<DocumentIndexPort> indexes() {
        return indexes;
    }

    private List<String> forEachIndex(String what, Consumer<DocumentIndexPort> action) {
        List<String> failed = new ArrayList<>();
        for (DocumentIndexPort port : indexes) {
            try {
                action.accept(port);
            } catch (IndexingException failure) {
                // Logged, not rethrown: MongoDB already holds the document, and failing the
                // caller's request would invite a retry that duplicates it.
                log.error("Could not {} in {} — this index is now behind MongoDB. "
                        + "Repair with POST /api/v1/admin/reindex.", what, port.name(), failure);
                failed.add(port.name());
            }
        }
        return failed;
    }

    /** True when at least one index is configured, so callers can skip pointless work. */
    public boolean hasIndexes() {
        return !indexes.isEmpty();
    }

    public String describe() {
        return indexes.isEmpty() ? "none"
                : indexes.stream().map(DocumentIndexPort::name).collect(Collectors.joining(", "));
    }
}
