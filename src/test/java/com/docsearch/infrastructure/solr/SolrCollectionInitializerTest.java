package com.docsearch.infrastructure.solr;

import com.docsearch.config.SolrProperties;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Solr is a derived index, not the source of truth, so an unreachable Solr must degrade to a
 * warning rather than stop the application. These tests pin that, because the interesting
 * failure does not arrive as the checked exception the happy-path code expects.
 */
class SolrCollectionInitializerTest {

    private static SolrProperties properties() {
        return new SolrProperties(true, "localhost:2181", "documents", true, 1, 1, 15_000);
    }

    @Test
    void anUnreachableZooKeeperDoesNotPreventStartup() throws Exception {
        // The real failure from a dead ensemble: SolrJ wraps the ZooKeeper timeout in
        // SolrException, which is a RuntimeException and so slips past a catch of
        // SolrServerException | IOException. Startup then dies inside the
        // ApplicationReadyEvent listener, taking every MongoDB-backed endpoint with it.
        SolrClient client = mock(SolrClient.class);
        when(client.request(any(), nullable(String.class)))
                .thenThrow(new SolrException(SolrException.ErrorCode.SERVER_ERROR,
                        "Could not connect to ZooKeeper localhost:2181 within 15000 ms"));

        SolrCollectionInitializer initializer =
                new SolrCollectionInitializer(client, properties());

        assertThatCode(initializer::createCollectionIfMissing).doesNotThrowAnyException();
    }

    @Test
    void aServerSideSolrFailureDoesNotPreventStartupEither() throws Exception {
        SolrClient client = mock(SolrClient.class);
        when(client.request(any(), nullable(String.class)))
                .thenThrow(new SolrServerException("collection admin request refused"));

        SolrCollectionInitializer initializer =
                new SolrCollectionInitializer(client, properties());

        assertThatCode(initializer::createCollectionIfMissing).doesNotThrowAnyException();
    }

    @Test
    void anIoFailureDoesNotPreventStartupEither() throws Exception {
        SolrClient client = mock(SolrClient.class);
        when(client.request(any(), nullable(String.class)))
                .thenThrow(new IOException("connection reset"));

        SolrCollectionInitializer initializer =
                new SolrCollectionInitializer(client, properties());

        assertThatCode(initializer::createCollectionIfMissing).doesNotThrowAnyException();
    }
}
