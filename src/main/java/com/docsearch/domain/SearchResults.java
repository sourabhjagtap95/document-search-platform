package com.docsearch.domain;

import java.util.List;

/**
 * One page of results.
 *
 * @param engine which backend answered — worth carrying because two engines can answer the same
 *               query differently, and a caller comparing them needs to know which is which
 * @param tookMs engine-reported query time, not wall clock: OpenSearch's {@code took}, Solr's
 *               {@code QTime}. Excludes network and deserialisation.
 */
public record SearchResults(String engine, long total, int page, int size, long tookMs,
                            List<SearchHit> hits) {

    public SearchResults {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }

    public static SearchResults empty(String engine, int page, int size) {
        return new SearchResults(engine, 0, page, size, 0, List.of());
    }
}
