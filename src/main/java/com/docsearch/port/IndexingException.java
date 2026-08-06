package com.docsearch.port;

/**
 * A search index could not be written to or read from.
 *
 * <p>One exception type across both engines, so callers can decide what to do about a
 * failed projection without importing OpenSearch or Solr classes. {@code indexName}
 * matters because with several indexes in play, "which one failed" is the first question.
 */
public class IndexingException extends RuntimeException {

    private final String indexName;

    public IndexingException(String indexName, String message, Throwable cause) {
        super("[" + indexName + "] " + message, cause);
        this.indexName = indexName;
    }

    public String indexName() {
        return indexName;
    }
}
