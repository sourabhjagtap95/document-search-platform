package com.docsearch.infrastructure.opensearch;

import com.docsearch.domain.SearchDocument;
import com.docsearch.port.IndexingException;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.ErrorCause;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The mirror of {@code SolrDocumentIndexerTest}: every failure has to leave this adapter as an
 * {@link IndexingException}, because that is the only type {@code DocumentIndexingService}
 * catches.
 *
 * <p>An unreachable cluster already arrived as {@code IOException} — the client wraps a refused
 * connection — so that path was covered. The gap is {@link OpenSearchException}, which is
 * unchecked and is what the client throws when the cluster answers with an error rather than
 * failing to answer: a read-only index block, a mapping conflict, a rejected bulk write. Those
 * are ordinary operational states, and each one would otherwise 500 a request whose MongoDB
 * write had already succeeded.
 */
class OpenSearchDocumentIndexerTest {

    private static final SearchDocument DOC = new SearchDocument(
            "id-1", "t", "c", "a", "cat", List.of("tag"), Instant.EPOCH, Instant.EPOCH);

    /** What the cluster returns when the index is blocked for writes. */
    private static OpenSearchException clusterBlock() {
        return new OpenSearchException(ErrorResponse.of(builder -> builder
                .status(403)
                .error(ErrorCause.of(cause -> cause
                        .type("cluster_block_exception")
                        .reason("index [documents] blocked by: [FORBIDDEN/12/index read-only]")))));
    }

    @Test
    void indexReportsAClusterErrorAsAnIndexingException() throws Exception {
        OpenSearchDocumentRepository repository = mock(OpenSearchDocumentRepository.class);
        doThrow(clusterBlock()).when(repository).save(any());

        assertThatThrownBy(() -> new OpenSearchDocumentIndexer(repository).index(DOC))
                .isInstanceOf(IndexingException.class)
                .hasMessageContaining("[opensearch]")
                .hasMessageContaining("id-1");
    }

    @Test
    void indexAllReportsAClusterErrorAsAnIndexingException() throws Exception {
        OpenSearchDocumentRepository repository = mock(OpenSearchDocumentRepository.class);
        when(repository.saveAll(any())).thenThrow(clusterBlock());

        assertThatThrownBy(() -> new OpenSearchDocumentIndexer(repository).indexAll(List.of(DOC)))
                .isInstanceOf(IndexingException.class)
                .hasMessageContaining("[opensearch]");
    }

    @Test
    void deleteReportsAClusterErrorAsAnIndexingException() throws Exception {
        OpenSearchDocumentRepository repository = mock(OpenSearchDocumentRepository.class);
        when(repository.deleteById(anyString())).thenThrow(clusterBlock());

        assertThatThrownBy(() -> new OpenSearchDocumentIndexer(repository).delete("id-1"))
                .isInstanceOf(IndexingException.class)
                .hasMessageContaining("[opensearch]");
    }

    @Test
    void clearReportsAClusterErrorAsAnIndexingException() throws Exception {
        OpenSearchDocumentRepository repository = mock(OpenSearchDocumentRepository.class);
        doThrow(clusterBlock()).when(repository).deleteAll();

        assertThatThrownBy(() -> new OpenSearchDocumentIndexer(repository).clear())
                .isInstanceOf(IndexingException.class)
                .hasMessageContaining("[opensearch]");
    }

    @Test
    void countReportsAClusterErrorAsAnIndexingException() throws Exception {
        OpenSearchDocumentRepository repository = mock(OpenSearchDocumentRepository.class);
        when(repository.count()).thenThrow(clusterBlock());

        assertThatThrownBy(() -> new OpenSearchDocumentIndexer(repository).count())
                .isInstanceOf(IndexingException.class)
                .hasMessageContaining("[opensearch]");
    }

    @Test
    void anUnreachableClusterIsStillReportedAsAnIndexingException() throws Exception {
        // The already-covered path, kept so a future refactor cannot narrow the catch back
        // down to only the unchecked case.
        OpenSearchDocumentRepository repository = mock(OpenSearchDocumentRepository.class);
        doThrow(new IOException("Connection refused")).when(repository).save(any());

        assertThatThrownBy(() -> new OpenSearchDocumentIndexer(repository).index(DOC))
                .isInstanceOf(IndexingException.class)
                .hasMessageContaining("[opensearch]");
    }
}
