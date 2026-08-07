package com.docsearch.infrastructure.solr;

import com.docsearch.domain.SearchQuery;
import com.docsearch.domain.SortBy;
import org.apache.solr.client.solrj.SolrQuery;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Turns a {@link SearchQuery} into a Solr query.
 *
 * <p>The counterpart of {@code DocumentsQueryTranslator}, and comparing the two is the point.
 * Solr's vocabulary differs — {@code q} and {@code qf} and {@code fq} rather than {@code must}
 * and {@code filter} — but the distinction is identical: <strong>{@code q} is query context,
 * {@code fq} is filter context.</strong> Two engines designed independently arrived at the same
 * split, which is why the split is worth teaching as a principle rather than as syntax.
 *
 * <p>Each filter is added as its own {@code fq} rather than being concatenated, because Solr
 * caches filter queries individually — one {@code fq} per concern gets reused across queries
 * that share it.
 *
 * <p>A pure function: no client, no network.
 */
@Component
public class DocumentsSolrQueryTranslator {

    private static final String MATCH_ALL = "*:*";
    /** Same weighting as the OpenSearch translator, deliberately. */
    private static final String QUERY_FIELDS = "title^3 content";
    /** Solr cannot sort on the analysed `title`; the indexer writes this exact copy. */
    private static final String TITLE_SORT_FIELD = "titleSort";
    private static final String CREATED_AT = "createdAt";

    public SolrQuery toSolrQuery(SearchQuery query) {
        SolrQuery solr = new SolrQuery();

        // Query context: scored.
        solr.setQuery(query.hasText() ? query.text() : MATCH_ALL);
        solr.setParam("defType", "edismax");
        solr.setParam("qf", QUERY_FIELDS);

        // Filter context: not scored, and cached per fq.
        if (!query.categories().isEmpty()) {
            solr.addFilterQuery(anyOf("category", query.categories()));
        }
        if (!query.tags().isEmpty()) {
            solr.addFilterQuery(anyOf("tags", query.tags()));
        }
        if (query.author() != null) {
            solr.addFilterQuery(anyOf("author", List.of(query.author())));
        }
        if (query.hasDateWindow()) {
            solr.addFilterQuery(dateRange(query.createdAfter(), query.createdBefore()));
        }

        solr.setStart(query.offset());
        solr.setRows(query.size());
        applySort(solr, query.sort());

        // Solr returns no score unless asked; OpenSearch always includes it.
        solr.setFields("*,score");

        if (query.highlight()) {
            solr.setHighlight(true);
            solr.setParam("hl.fl", "title,content");
            solr.setHighlightSimplePre("<em>");
            solr.setHighlightSimplePost("</em>");
        }

        return solr;
    }

    private static void applySort(SolrQuery solr, SortBy sort) {
        switch (sort) {
            case RELEVANCE -> solr.addSort("score", SolrQuery.ORDER.desc);
            case NEWEST -> solr.addSort(CREATED_AT, SolrQuery.ORDER.desc);
            case OLDEST -> solr.addSort(CREATED_AT, SolrQuery.ORDER.asc);
            case TITLE -> solr.addSort(TITLE_SORT_FIELD, SolrQuery.ORDER.asc);
        }
    }

    /** {@code field:("a" OR "b")} — exact values, so each is quoted rather than analysed. */
    private static String anyOf(String field, Collection<String> values) {
        return values.stream()
                .map(value -> "\"" + escape(value) + "\"")
                .collect(Collectors.joining(" OR ", field + ":(", ")"));
    }

    private static String dateRange(Instant after, Instant before) {
        return CREATED_AT + ":[" + bound(after) + " TO " + bound(before) + "]";
    }

    /** Solr spells an open bound {@code *}. */
    private static String bound(Instant instant) {
        return instant == null ? "*" : instant.toString();
    }

    /**
     * A quote inside a quoted term would close it early and turn the rest of the value into
     * query syntax, so it is escaped rather than trusted.
     */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
