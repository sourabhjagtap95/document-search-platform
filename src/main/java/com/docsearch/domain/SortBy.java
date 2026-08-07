package com.docsearch.domain;

/**
 * How results are ordered.
 *
 * <p>Named {@code SortBy} rather than {@code SortOrder} on purpose: OpenSearch's client has its
 * own {@code SortOrder}, and the translator needs both in one file.
 */
public enum SortBy {
    /** Score descending. Only meaningful when the query carries text. */
    RELEVANCE,
    NEWEST,
    OLDEST,
    TITLE
}
