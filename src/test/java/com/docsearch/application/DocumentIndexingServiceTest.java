package com.docsearch.application;

import com.docsearch.domain.SearchDocument;
import com.docsearch.port.DocumentIndexPort;
import com.docsearch.port.IndexingException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The failure model is the substance of {@link DocumentIndexingService}, so these tests are
 * mostly about what happens when an index misbehaves — not about the happy path.
 *
 * <p>Hand-written fakes rather than mocks: the assertions are about isolation between two
 * collaborators, which reads better with real objects that record what they were asked to do.
 */
class DocumentIndexingServiceTest {

    private static final SearchDocument DOC = new SearchDocument(
            "id-1", "t", "c", "a", "cat", List.of(), Instant.EPOCH, Instant.EPOCH);

    /** Records calls; optionally fails on demand. */
    private static final class FakeIndex implements DocumentIndexPort {
        private final String name;
        private final boolean failing;
        private final List<String> calls = new ArrayList<>();
        private long count;

        FakeIndex(String name, boolean failing) {
            this(name, failing, 0);
        }

        FakeIndex(String name, boolean failing, long count) {
            this.name = name;
            this.failing = failing;
            this.count = count;
        }

        private void record(String call) {
            calls.add(call);
            if (failing) {
                throw new IndexingException(name, call + " failed", new RuntimeException("boom"));
            }
        }

        @Override public String name() { return name; }
        @Override public void index(SearchDocument document) { record("index:" + document.id()); }
        @Override public int indexAll(List<SearchDocument> documents) { record("indexAll"); return documents.size(); }
        @Override public boolean delete(String id) { record("delete:" + id); return true; }
        @Override public void clear() { record("clear"); }
        @Override public long count() { record("count"); return count; }
    }

    @Test
    void projectsIntoEveryConfiguredIndex() {
        FakeIndex one = new FakeIndex("one", false);
        FakeIndex two = new FakeIndex("two", false);

        List<String> failed = new DocumentIndexingService(List.of(one, two)).index(DOC);

        assertThat(failed).isEmpty();
        assertThat(one.calls).containsExactly("index:id-1");
        assertThat(two.calls).containsExactly("index:id-1");
    }

    @Test
    void oneFailingIndexDoesNotStopTheOthers() {
        // Solr being down must not prevent OpenSearch from being updated.
        FakeIndex broken = new FakeIndex("broken", true);
        FakeIndex healthy = new FakeIndex("healthy", false);

        List<String> failed = new DocumentIndexingService(List.of(broken, healthy)).index(DOC);

        assertThat(failed).containsExactly("broken");
        assertThat(healthy.calls).containsExactly("index:id-1");
    }

    @Test
    void aFailureIsReportedRatherThanThrown() {
        // Throwing would fail a request whose MongoDB write already succeeded, and the
        // caller would retry it — creating a duplicate.
        FakeIndex broken = new FakeIndex("broken", true);

        List<String> failed = new DocumentIndexingService(List.of(broken)).index(DOC);

        assertThat(failed).containsExactly("broken");
    }

    @Test
    void deleteIsAttemptedOnEveryIndexEvenAfterAFailure() {
        FakeIndex broken = new FakeIndex("broken", true);
        FakeIndex healthy = new FakeIndex("healthy", false);

        List<String> failed = new DocumentIndexingService(List.of(broken, healthy)).delete("id-1");

        assertThat(failed).containsExactly("broken");
        assertThat(healthy.calls).containsExactly("delete:id-1");
    }

    @Test
    void countsAreReportedPerIndex() {
        DocumentIndexingService service = new DocumentIndexingService(
                List.of(new FakeIndex("one", false, 7), new FakeIndex("two", false, 7)));

        assertThat(service.counts()).containsEntry("one", 7L).containsEntry("two", 7L);
    }

    @Test
    void anUnreachableIndexCountsAsMinusOneNotZero() {
        // Zero would read as "the index is empty", which is a different problem with a
        // different fix. -1 says "we could not ask".
        DocumentIndexingService service = new DocumentIndexingService(
                List.of(new FakeIndex("broken", true), new FakeIndex("healthy", false, 3)));

        assertThat(service.counts())
                .containsEntry("broken", -1L)
                .containsEntry("healthy", 3L);
    }

    @Test
    void reportsWhichIndexesAreConfigured() {
        DocumentIndexingService service = new DocumentIndexingService(
                List.of(new FakeIndex("opensearch", false), new FakeIndex("solr", false)));

        assertThat(service.names()).containsExactly("opensearch", "solr");
        assertThat(service.describe()).isEqualTo("opensearch, solr");
        assertThat(service.hasIndexes()).isTrue();
    }

    @Test
    void toleratesHavingNoIndexesAtAll() {
        // Both engines can be switched off; the source of truth still works.
        DocumentIndexingService service = new DocumentIndexingService(List.of());

        assertThat(service.index(DOC)).isEmpty();
        assertThat(service.delete("id-1")).isEmpty();
        assertThat(service.hasIndexes()).isFalse();
        assertThat(service.describe()).isEqualTo("none");
    }
}
