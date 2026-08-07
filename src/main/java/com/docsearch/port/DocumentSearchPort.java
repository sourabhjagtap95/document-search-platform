package com.docsearch.port;

import com.docsearch.domain.SearchQuery;
import com.docsearch.domain.SearchResults;

/**
 * A search engine that can answer a {@link SearchQuery}.
 *
 * <p>Separate from {@code DocumentIndexPort}, and the reason is a genuine asymmetry rather than
 * tidiness. Indexing <strong>broadcasts</strong>: every configured index must receive every
 * write, so {@code DocumentIndexingService} loops over all of them. Searching
 * <strong>selects</strong>: two engines compute relevance from different term statistics, so
 * their scores cannot be compared and two result sets cannot be merged into one ranking.
 *
 * <p>Framework-free, like every other type in this package.
 *
 * <p>Failures arrive as {@link IndexingException} — whose contract already covers an index that
 * could not be read from — and, unlike on the write path, they are <em>not</em> swallowed. See
 * {@code SearchService} for why a search must fail loudly where a write may degrade.
 */
public interface DocumentSearchPort {

    /** Short stable name, matching the indexer's: {@code opensearch} or {@code solr}. */
    String name();

    SearchResults search(SearchQuery query);
}
