package com.docsearch.domain;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * One search, expressed without reference to any engine.
 *
 * <p>No field names, no boost syntax, no clause types — that vocabulary belongs to the
 * translators. What survives here is the caller's <em>intent</em>, which is the only thing both
 * OpenSearch and Solr can be asked to honour.
 *
 * <p>Build with {@link #of}, which applies the defaulting rules. The canonical constructor is
 * deliberately dumb so the rules live in exactly one place.
 *
 * @param text          free-text query, or {@code null}
 * @param categories    exact category values; any match (OR)
 * @param tags          exact tag values; any match (OR)
 * @param author        exact author, or {@code null}
 * @param createdAfter  inclusive lower bound, or {@code null}
 * @param createdBefore inclusive upper bound, or {@code null}
 * @param highlight     derived from {@code text}, not supplied by the caller
 */
public record SearchQuery(
        String text,
        Set<String> categories,
        Set<String> tags,
        String author,
        Instant createdAfter,
        Instant createdBefore,
        SortBy sort,
        int page,
        int size,
        boolean highlight
) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static SearchQuery of(String text,
                                 Set<String> categories,
                                 Set<String> tags,
                                 String author,
                                 Instant createdAfter,
                                 Instant createdBefore,
                                 SortBy sort,
                                 int page,
                                 int size) {

        String cleanText = trimToNull(text);
        boolean hasText = cleanText != null;

        // Relevance without text ranks by identical scores, which is arbitrary order wearing a
        // convincing disguise. Fall back to newest instead.
        SortBy effectiveSort = sort != null ? sort : (hasText ? SortBy.RELEVANCE : SortBy.NEWEST);

        return new SearchQuery(
                cleanText,
                categories == null ? Set.of() : Set.copyOf(categories),
                tags == null ? Set.of() : Set.copyOf(tags),
                trimToNull(author),
                createdAfter,
                createdBefore,
                effectiveSort,
                page,
                size,
                hasText);
    }

    public boolean hasText() {
        return text != null;
    }

    /** Where this page starts — {@code from} in OpenSearch, {@code start} in Solr. */
    public int offset() {
        return page * size;
    }

    public boolean hasDateWindow() {
        return createdAfter != null || createdBefore != null;
    }

    /** Fields highlighted when highlighting is on. */
    public static List<String> highlightFields() {
        return List.of("title", "content");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
