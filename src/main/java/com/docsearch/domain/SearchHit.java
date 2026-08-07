package com.docsearch.domain;

import java.util.List;
import java.util.Map;

/**
 * One matching document, with why it matched.
 *
 * @param score      relevance score. Comparable within one result set from one engine, and
 *                   <strong>not</strong> comparable across engines — the term statistics differ.
 * @param highlights field name to matched snippets; empty when highlighting was off
 */
public record SearchHit(SearchDocument document, double score, Map<String, List<String>> highlights) {

    public SearchHit(SearchDocument document, double score) {
        this(document, score, Map.of());
    }
}
