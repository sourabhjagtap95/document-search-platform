package com.docsearch.infrastructure.opensearch;

import com.docsearch.config.OpenSearchProperties;
import com.docsearch.domain.SearchQuery;
import com.docsearch.port.IndexingException;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

import java.io.IOException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mirrors the indexer tests: every failure must leave the adapter as an
 * {@link IndexingException}, including the unchecked {@link OpenSearchException} the client
 * throws when the cluster answers with an error rather than failing to answer.
 */
class OpenSearchDocumentSearcherTest {

    private static final SearchQuery QUERY =
            SearchQuery.of("opensearch", Set.of(), Set.of(), null, null, null, null, 0, 20);

    private static OpenSearchProperties properties() {
        return new OpenSearchProperties(true, "http://localhost:9200", "documents", false);
    }

    private static OpenSearchDocumentSearcher searcherThatFailsWith(Throwable failure) throws IOException {
        OpenSearchClient client = mock(OpenSearchClient.class);
        when(client.search(any(SearchRequest.class), any(Class.class))).thenThrow(failure);
        return new OpenSearchDocumentSearcher(client, new DocumentsQueryTranslator(), properties());
    }

    @Test
    void anUnreachableClusterBecomesAnIndexingException() throws Exception {
        assertThatThrownBy(() -> searcherThatFailsWith(new IOException("Connection refused")).search(QUERY))
                .isInstanceOf(IndexingException.class)
                .hasMessageContaining("[opensearch]");
    }

    @Test
    void aClusterErrorBecomesAnIndexingException() throws Exception {
        OpenSearchException clusterError = new OpenSearchException(ErrorResponse.of(builder -> builder
                .status(400)
                .error(ErrorCause.of(cause -> cause
                        .type("search_phase_execution_exception")
                        .reason("all shards failed")))));

        assertThatThrownBy(() -> searcherThatFailsWith(clusterError).search(QUERY))
                .isInstanceOf(IndexingException.class)
                .hasMessageContaining("[opensearch]");
    }

    @Test
    void reportsItsNameAsOpensearch() {
        OpenSearchDocumentSearcher searcher = new OpenSearchDocumentSearcher(
                mock(OpenSearchClient.class), new DocumentsQueryTranslator(), properties());

        org.assertj.core.api.Assertions.assertThat(searcher.name()).isEqualTo("opensearch");
    }
}
