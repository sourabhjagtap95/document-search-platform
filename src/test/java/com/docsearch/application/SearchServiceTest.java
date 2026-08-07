package com.docsearch.application;

import com.docsearch.config.SearchProperties;
import com.docsearch.domain.SearchQuery;
import com.docsearch.domain.SearchResults;
import com.docsearch.port.DocumentSearchPort;
import com.docsearch.port.IndexingException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Selection is the substance here, because it is where search differs structurally from
 * indexing: a write goes to every backend, a search goes to exactly one. Merging two engines'
 * results would mean ranking by scores computed from different term statistics.
 */
class SearchServiceTest {

    private static final SearchQuery QUERY =
            SearchQuery.of("opensearch", Set.of(), Set.of(), null, null, null, null, 0, 20);

    /** Records whether it was asked; optionally fails the way an unreachable engine fails. */
    private static final class FakeBackend implements DocumentSearchPort {
        private final String name;
        private final boolean failing;
        private boolean wasSearched;

        FakeBackend(String name) {
            this(name, false);
        }

        FakeBackend(String name, boolean failing) {
            this.name = name;
            this.failing = failing;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public SearchResults search(SearchQuery query) {
            wasSearched = true;
            if (failing) {
                throw new IndexingException(name, "search failed", new RuntimeException("boom"));
            }
            return new SearchResults(name, 1, query.page(), query.size(), 5, List.of());
        }
    }

    private static SearchService service(List<DocumentSearchPort> backends, String configured) {
        return new SearchService(backends, new SearchProperties(configured));
    }

    @Test
    void usesTheConfiguredBackendWhenTheRequestDoesNotNameOne() {
        FakeBackend opensearch = new FakeBackend("opensearch");
        FakeBackend solr = new FakeBackend("solr");

        SearchResults results = service(List.of(opensearch, solr), "opensearch").search(QUERY, null);

        assertThat(results.engine()).isEqualTo("opensearch");
        assertThat(opensearch.wasSearched).isTrue();
        assertThat(solr.wasSearched).as("only one backend answers a search").isFalse();
    }

    @Test
    void anExplicitEngineOverridesTheConfiguredDefault() {
        FakeBackend opensearch = new FakeBackend("opensearch");
        FakeBackend solr = new FakeBackend("solr");

        SearchResults results = service(List.of(opensearch, solr), "opensearch").search(QUERY, "solr");

        assertThat(results.engine()).isEqualTo("solr");
        assertThat(solr.wasSearched).isTrue();
        assertThat(opensearch.wasSearched).isFalse();
    }

    @Test
    void theEngineNameIsCaseInsensitiveAndTrimmed() {
        FakeBackend solr = new FakeBackend("solr");

        assertThat(service(List.of(solr), "solr").search(QUERY, "  SOLR ").engine()).isEqualTo("solr");
    }

    @Test
    void aBlankEngineIsTreatedAsAbsentRatherThanUnknown() {
        FakeBackend opensearch = new FakeBackend("opensearch");

        assertThat(service(List.of(opensearch), "opensearch").search(QUERY, "  ").engine())
                .isEqualTo("opensearch");
    }

    @Test
    void anUnknownEngineIsTheCallersMistake() {
        // A client asked for infrastructure that does not exist — that is a 400, not a 500.
        FakeBackend opensearch = new FakeBackend("opensearch");

        assertThatThrownBy(() -> service(List.of(opensearch), "opensearch").search(QUERY, "elastic"))
                .isInstanceOf(UnknownSearchBackendException.class)
                .hasMessageContaining("elastic");
    }

    @Test
    void anEngineThatIsSwitchedOffIsAlsoUnknown() {
        // solr.enabled=false removes the bean, so naming it is indistinguishable from a typo.
        FakeBackend opensearch = new FakeBackend("opensearch");

        assertThatThrownBy(() -> service(List.of(opensearch), "opensearch").search(QUERY, "solr"))
                .isInstanceOf(UnknownSearchBackendException.class);
    }

    @Test
    void aMisconfiguredDefaultBackendFailsAsUnavailableNotAsTheCallersFault() {
        FakeBackend solr = new FakeBackend("solr");

        assertThatThrownBy(() -> service(List.of(solr), "opensearch").search(QUERY, null))
                .isInstanceOf(IndexingException.class);
    }

    @Test
    void havingNoBackendsAtAllIsUnavailable() {
        assertThatThrownBy(() -> service(List.of(), "opensearch").search(QUERY, null))
                .isInstanceOf(IndexingException.class);
    }

    @Test
    void anEngineFailurePropagatesRatherThanBeingSwallowed() {
        // The opposite of the write path. A failed index write is logged and tolerated because
        // MongoDB still holds the document; a failed search has no correct fallback, so it must
        // surface. Returning empty results here would be a lie.
        FakeBackend broken = new FakeBackend("opensearch", true);

        assertThatThrownBy(() -> service(List.of(broken), "opensearch").search(QUERY, null))
                .isInstanceOf(IndexingException.class);
    }

    @Test
    void reportsWhichBackendsAreAvailable() {
        SearchService service = service(
                List.of(new FakeBackend("opensearch"), new FakeBackend("solr")), "opensearch");

        assertThat(service.availableBackends()).containsExactly("opensearch", "solr");
    }
}
