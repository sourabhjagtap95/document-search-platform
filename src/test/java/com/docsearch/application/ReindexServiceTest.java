package com.docsearch.application;

import com.docsearch.application.ReindexService.ReindexOutcome;
import com.docsearch.domain.SearchDocument;
import com.docsearch.infrastructure.mongo.DocumentEntity;
import com.docsearch.infrastructure.mongo.DocumentRepository;
import com.docsearch.port.DocumentIndexPort;
import com.docsearch.port.IndexingException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reindexing is the repair half of the Day 5 design: the write path may leave an index behind
 * precisely because the index can be rebuilt from MongoDB. So what matters here is that the
 * rebuild reads the source of truth in pages rather than all at once, and that one broken
 * engine cannot stop the other from being repaired.
 */
class ReindexServiceTest {

    private static final int PAGE_SIZE = 500;

    private static SearchDocument doc(int n) {
        return new SearchDocument("id-" + n, "t" + n, "c", "a", "cat",
                List.of(), Instant.EPOCH, Instant.EPOCH);
    }

    /** Records what it was asked to do; optionally fails on a chosen call. */
    private static final class FakeIndex implements DocumentIndexPort {
        private final String name;
        private final String failOn;
        private final List<String> calls = new ArrayList<>();
        private int indexed;

        FakeIndex(String name) {
            this(name, null);
        }

        FakeIndex(String name, String failOn) {
            this.name = name;
            this.failOn = failOn;
        }

        private void record(String call) {
            calls.add(call);
            if (call.equals(failOn)) {
                throw new IndexingException(name, call + " failed", new RuntimeException("boom"));
            }
        }

        @Override public String name() { return name; }
        @Override public void index(SearchDocument document) { record("index"); }
        @Override public void clear() { record("clear"); }
        @Override public long count() { record("count"); return indexed; }
        @Override public boolean delete(String id) { record("delete"); return true; }

        @Override
        public int indexAll(List<SearchDocument> documents) {
            record("indexAll:" + documents.size());
            indexed += documents.size();
            return documents.size();
        }
    }

    /** Stubs MongoDB paging with {@code total} documents spread across pages. */
    private static DocumentRepository repositoryHolding(int total) {
        DocumentRepository repository = mock(DocumentRepository.class);
        when(repository.count()).thenReturn((long) total);
        when(repository.findAll(any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(0);
            int from = (int) pageable.getOffset();
            int to = Math.min(from + pageable.getPageSize(), total);
            List<DocumentEntity> page = from >= to ? List.of()
                    : java.util.stream.IntStream.range(from, to)
                            .mapToObj(n -> DocumentEntity.fromDomain(doc(n)))
                            .toList();
            return new PageImpl<>(page, PageRequest.of(pageable.getPageNumber(),
                    pageable.getPageSize()), total);
        });
        return repository;
    }

    private static ReindexService serviceFor(DocumentRepository repository, DocumentIndexPort... indexes) {
        return new ReindexService(repository, new DocumentIndexingService(List.of(indexes)));
    }

    @Test
    void rebuildsEveryDocumentFromMongo() {
        FakeIndex index = new FakeIndex("opensearch");

        Map<String, ReindexOutcome> outcomes =
                serviceFor(repositoryHolding(10), index).reindexAll(false);

        assertThat(outcomes.get("opensearch").documentsIndexed()).isEqualTo(10);
        assertThat(outcomes.get("opensearch").succeeded()).isTrue();
        assertThat(outcomes.get("opensearch").error()).isNull();
    }

    @Test
    void readsMongoInPagesRatherThanAllAtOnce() {
        // A single findAll would exhaust the heap on a large collection, so the page size is
        // part of the contract, not an implementation detail.
        FakeIndex index = new FakeIndex("opensearch");

        serviceFor(repositoryHolding(PAGE_SIZE * 2 + 3), index).reindexAll(false);

        assertThat(index.calls).containsExactly(
                "indexAll:" + PAGE_SIZE, "indexAll:" + PAGE_SIZE, "indexAll:3");
    }

    @Test
    void stopsAfterAShortPageWithoutAnExtraEmptyRead() {
        // An exact multiple of the page size is the case that needs one more read to learn
        // it is done; anything less must not trigger a pointless round trip.
        FakeIndex index = new FakeIndex("opensearch");

        serviceFor(repositoryHolding(10), index).reindexAll(false);

        assertThat(index.calls).containsExactly("indexAll:10");
    }

    @Test
    void anEmptyCollectionIndexesNothingAndStillSucceeds() {
        FakeIndex index = new FakeIndex("opensearch");

        Map<String, ReindexOutcome> outcomes = serviceFor(repositoryHolding(0), index).reindexAll(false);

        assertThat(outcomes.get("opensearch").documentsIndexed()).isZero();
        assertThat(outcomes.get("opensearch").succeeded()).isTrue();
        assertThat(index.calls).isEmpty();
    }

    @Test
    void clearFirstDropsIndexContentBeforeRebuilding() {
        // This is what removes documents deleted from Mongo while an index was unreachable.
        FakeIndex index = new FakeIndex("opensearch");

        serviceFor(repositoryHolding(3), index).reindexAll(true);

        assertThat(index.calls).containsExactly("clear", "indexAll:3");
    }

    @Test
    void withoutClearTheIndexIsNotDropped() {
        FakeIndex index = new FakeIndex("opensearch");

        serviceFor(repositoryHolding(3), index).reindexAll(false);

        assertThat(index.calls).doesNotContain("clear");
    }

    @Test
    void oneUnreachableEngineDoesNotPreventRepairingTheOther() {
        FakeIndex broken = new FakeIndex("solr", "indexAll:3");
        FakeIndex healthy = new FakeIndex("opensearch");

        Map<String, ReindexOutcome> outcomes =
                serviceFor(repositoryHolding(3), broken, healthy).reindexAll(false);

        assertThat(outcomes.get("solr").succeeded()).isFalse();
        assertThat(outcomes.get("solr").error()).contains("[solr]");
        assertThat(outcomes.get("opensearch").succeeded()).isTrue();
        assertThat(outcomes.get("opensearch").documentsIndexed()).isEqualTo(3);
    }

    @Test
    void aFailedClearAbandonsThatIndexWithoutIndexingIntoIt() {
        // Rebuilding on top of content that was supposed to be dropped would leave exactly
        // the stale documents clear=true was asked to remove.
        FakeIndex broken = new FakeIndex("solr", "clear");

        Map<String, ReindexOutcome> outcomes =
                serviceFor(repositoryHolding(3), broken).reindexAll(true);

        assertThat(outcomes.get("solr").succeeded()).isFalse();
        assertThat(broken.calls).containsExactly("clear");
    }

    @Test
    void reportsTheMongoCountForComparisonAgainstTheIndexes() {
        assertThat(serviceFor(repositoryHolding(42), new FakeIndex("opensearch"))
                .documentsInSourceOfTruth()).isEqualTo(42);
    }
}
