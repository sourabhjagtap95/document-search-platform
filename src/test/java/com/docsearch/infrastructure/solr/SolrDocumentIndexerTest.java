package com.docsearch.infrastructure.solr;

import com.docsearch.config.SolrProperties;
import com.docsearch.domain.SearchDocument;
import com.docsearch.port.IndexingException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The adapter's job is to make every Solr failure arrive as an {@link IndexingException}, and
 * that is load-bearing rather than tidy: {@code DocumentIndexingService} catches exactly that
 * type. Anything that escapes as a different exception propagates all the way out and fails a
 * request whose MongoDB write already succeeded — the outcome the whole Day 5 failure model
 * exists to avoid.
 *
 * <p>The failure used here is the one a stopped Solr node actually produces: a
 * {@code SolrException} carrying "Could not find a healthy node to handle the request".
 * It is unchecked, so it slips past a catch of {@code SolrServerException | IOException}.
 */
class SolrDocumentIndexerTest {

    private static final SearchDocument DOC = new SearchDocument(
            "id-1", "t", "c", "a", "cat", List.of("tag"), Instant.EPOCH, Instant.EPOCH);

    /**
     * A client whose every call fails the way a stopped Solr node fails.
     *
     * <p>A real subclass rather than a Mockito mock, and that distinction matters here. Every
     * convenience method on {@code SolrClient} — {@code add}, {@code commit},
     * {@code deleteById}, {@code query} — funnels into {@code request}, so overriding that one
     * method makes each of them fail through the same path the running system uses. Mocking
     * {@code SolrClient} instead stubs the convenience methods themselves, so they return null
     * without ever reaching {@code request} and no failure is exercised at all.
     */
    private static SolrClient unreachableSolr() {
        return new SolrClient() {
            @Override
            public NamedList<Object> request(SolrRequest<?> request, String collection) {
                throw new SolrException(SolrException.ErrorCode.INVALID_STATE,
                        "Could not find a healthy node to handle the request.");
            }

            @Override
            public void close() {
            }
        };
    }

    private static SolrDocumentIndexer indexer() {
        return new SolrDocumentIndexer(unreachableSolr(),
                new SolrProperties(true, "localhost:2181", "documents", true, 1, 1, 15_000));
    }

    @Test
    void indexReportsAnUnreachableSolrAsAnIndexingException() {
        assertThatThrownBy(() -> indexer().index(DOC))
                .isInstanceOf(IndexingException.class)
                .hasMessageContaining("[solr]")
                .hasMessageContaining("id-1");
    }

    @Test
    void indexAllReportsAnUnreachableSolrAsAnIndexingException() {
        assertThatThrownBy(() -> indexer().indexAll(List.of(DOC)))
                .isInstanceOf(IndexingException.class)
                .hasMessageContaining("[solr]");
    }

    @Test
    void deleteReportsAnUnreachableSolrAsAnIndexingException() {
        assertThatThrownBy(() -> indexer().delete("id-1"))
                .isInstanceOf(IndexingException.class)
                .hasMessageContaining("[solr]");
    }

    @Test
    void clearReportsAnUnreachableSolrAsAnIndexingException() {
        assertThatThrownBy(() -> indexer().clear())
                .isInstanceOf(IndexingException.class)
                .hasMessageContaining("[solr]");
    }

    @Test
    void countReportsAnUnreachableSolrAsAnIndexingException() {
        // index-status calls this. Left unwrapped it turns the endpoint that is supposed to
        // reveal the drift into a 500 of its own.
        assertThatThrownBy(() -> indexer().count())
                .isInstanceOf(IndexingException.class)
                .hasMessageContaining("[solr]");
    }
}
