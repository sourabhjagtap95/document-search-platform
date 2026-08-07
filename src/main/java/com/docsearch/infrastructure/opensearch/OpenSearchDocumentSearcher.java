package com.docsearch.infrastructure.opensearch;

import com.docsearch.config.OpenSearchProperties;
import com.docsearch.domain.SearchDocument;
import com.docsearch.domain.SearchHit;
import com.docsearch.domain.SearchQuery;
import com.docsearch.domain.SearchResults;
import com.docsearch.port.DocumentSearchPort;
import com.docsearch.port.IndexingException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenSearch as a {@link DocumentSearchPort}.
 *
 * <p>Separate from {@code OpenSearchDocumentIndexer} so neither class does two jobs: one writes,
 * one reads, and they fail for different reasons at different times.
 *
 * <p>Both exception kinds are converted, for the reason spelled out on the indexer:
 * {@code IOException} means the cluster could not be reached, {@code OpenSearchException} is
 * unchecked and means it answered with an error. Only the first has to be caught to compile.
 */
@Component
@ConditionalOnProperty(name = "opensearch.enabled", matchIfMissing = true)
public class OpenSearchDocumentSearcher implements DocumentSearchPort {

    private final OpenSearchClient client;
    private final DocumentsQueryTranslator translator;
    private final String index;

    public OpenSearchDocumentSearcher(OpenSearchClient client,
                                      DocumentsQueryTranslator translator,
                                      OpenSearchProperties properties) {
        this.client = client;
        this.translator = translator;
        this.index = properties.documentsIndex();
    }

    @Override
    public String name() {
        return "opensearch";
    }

    @Override
    public SearchResults search(SearchQuery query) {
        SearchRequest request = translator.toSearchRequest(index, query);
        try {
            SearchResponse<SearchDocument> response = client.search(request, SearchDocument.class);
            return toResults(query, response);
        } catch (IOException | OpenSearchException failure) {
            throw new IndexingException(name(), "search failed", failure);
        }
    }

    private SearchResults toResults(SearchQuery query, SearchResponse<SearchDocument> response) {
        List<SearchHit> hits = new ArrayList<>();

        for (org.opensearch.client.opensearch.core.search.Hit<SearchDocument> hit : response.hits().hits()) {
            SearchDocument source = hit.source();
            if (source == null) {
                continue;
            }
            // _source omits the document id, so graft _id back on — as the repository does.
            hits.add(new SearchHit(
                    source.withId(hit.id()),
                    hit.score() == null ? 0.0 : hit.score(),
                    hit.highlight() == null ? Map.of() : hit.highlight()));
        }

        long total = response.hits().total() == null ? hits.size() : response.hits().total().value();
        return new SearchResults(name(), total, query.page(), query.size(), response.took(), hits);
    }
}
