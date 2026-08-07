package com.docsearch.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code hits} must never leak the caller's own list: a caller passing a mutable {@code ArrayList}
 * should not be able to change a result page after it has been handed off.
 */
class SearchResultsTest {

    @Test
    void nullHitsBecomeEmptyRatherThanNull() {
        SearchResults results = new SearchResults("opensearch", 0, 0, 20, 5, null);

        assertThat(results.hits()).isEmpty();
    }

    @Test
    void mutatingTheSourceListAfterConstructionDoesNotChangeTheResults() {
        List<SearchHit> mutable = new ArrayList<>();
        SearchResults results = new SearchResults("opensearch", 0, 0, 20, 5, mutable);

        mutable.add(new SearchHit(
                SearchDocument.create("t", "c", "a", "cat", List.of(), Instant.now()), 1.0));

        assertThat(results.hits()).isEmpty();
    }

    @Test
    void emptyFactoryHasNoHits() {
        assertThat(SearchResults.empty("opensearch", 0, 20).hits()).isEmpty();
    }
}
