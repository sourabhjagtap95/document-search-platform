package com.docsearch.infrastructure.solr;

import com.docsearch.config.SolrProperties;
import com.docsearch.domain.SearchQuery;
import com.docsearch.port.IndexingException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Same contract as every other adapter: failures leave as {@link IndexingException}. The failure
 * a stopped Solr node actually produces is an unchecked {@code SolrException}, which slips past
 * a catch of the checked pair — the bug this project already hit once on the write path.
 */
class SolrDocumentSearcherTest {

    private static final SearchQuery QUERY =
            SearchQuery.of("opensearch", Set.of(), Set.of(), null, null, null, null, 0, 20);

    private static SolrProperties properties() {
        return new SolrProperties(true, "localhost:2181", "documents", true, 1, 1, 15_000);
    }

    /**
     * A real subclass rather than a mock: every convenience method on {@code SolrClient} funnels
     * through {@code request}, so overriding that one method exercises the same path the running
     * system uses. Mocking the client stubs the convenience methods instead, and they never reach
     * {@code request} at all.
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

    @Test
    void anUnreachableSolrBecomesAnIndexingException() {
        SolrDocumentSearcher searcher = new SolrDocumentSearcher(
                unreachableSolr(), new DocumentsSolrQueryTranslator(), properties());

        assertThatThrownBy(() -> searcher.search(QUERY))
                .isInstanceOf(IndexingException.class)
                .hasMessageContaining("[solr]");
    }

    @Test
    void reportsItsNameAsSolr() {
        SolrDocumentSearcher searcher = new SolrDocumentSearcher(
                unreachableSolr(), new DocumentsSolrQueryTranslator(), properties());

        assertThat(searcher.name()).isEqualTo("solr");
    }
}
