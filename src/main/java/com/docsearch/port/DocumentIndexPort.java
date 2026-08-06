package com.docsearch.port;

import com.docsearch.domain.SearchDocument;

import java.util.List;

/**
 * A searchable projection of the documents held in MongoDB.
 *
 * <p>There are two implementations — OpenSearch and Apache Solr — and the point of the
 * interface is that the synchronisation logic is written once. Adding a third engine
 * means adding one bean, not touching the service that keeps them in step.
 *
 * <p>Lives in its own package, not in {@code application}, on purpose. Implementations
 * sit in {@code infrastructure}, and {@code application} already depends on
 * {@code infrastructure}; putting the interface in {@code application} would make the two
 * packages depend on each other and {@code ArchitectureRulesTest}'s cycle check would
 * fail the build. A package that both layers may depend on, and which depends on neither,
 * is the way out.
 *
 * <p>Deliberately framework-free: no Spring, no client types, and a single exception type
 * so callers do not have to know whether a failure arrived as an {@code IOException} or a
 * {@code SolrServerException}.
 */
public interface DocumentIndexPort {

    /** Short stable name used in logs and in the index-status response. */
    String name();

    /** Adds or replaces one document. */
    void index(SearchDocument document);

    /** Adds or replaces many documents in as few round trips as the engine allows. */
    int indexAll(List<SearchDocument> documents);

    /** @return {@code true} if a document was removed, {@code false} if it was not present */
    boolean delete(String id);

    /** Removes everything. Used before a full rebuild. */
    void clear();

    /** Number of documents currently searchable. */
    long count();
}
