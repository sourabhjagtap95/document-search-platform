package com.docsearch.domain;

/**
 * One searchable token produced by an analyzer.
 *
 * <p>{@code position} is what phrase queries compare — two tokens at the same position
 * are alternatives at that spot in the text, which is how "OpenSearch" can be stored
 * as both itself and its parts. {@code startOffset}/{@code endOffset} point back into
 * the original string, and are what highlighting uses to mark up the source text.
 */
public record AnalyzedToken(
        String token,
        String type,
        int position,
        int startOffset,
        int endOffset
) {
}
