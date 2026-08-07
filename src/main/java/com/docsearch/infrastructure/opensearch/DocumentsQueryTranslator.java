package com.docsearch.infrastructure.opensearch;

import com.docsearch.domain.SearchQuery;
import com.docsearch.domain.SortBy;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Turns a {@link SearchQuery} into an OpenSearch request.
 *
 * <p><strong>Query context versus filter context is the whole job.</strong> The free-text query
 * goes in {@code must}, where it is scored — how well a document matches is the question being
 * asked. Everything else goes in {@code filter}, where it is not scored: a category either
 * matches or it does not, and there is no such thing as matching it <em>well</em>.
 *
 * <p>Getting this wrong is invisible. A {@code term} clause in {@code must} returns exactly the
 * same documents; it just adds a constant to every score and gives up the filter cache. Nothing
 * fails, so only a test that inspects the built request can catch it.
 *
 * <p>A pure function, deliberately: no client, no network. That is what lets the mapping be
 * tested where there is no cluster to talk to.
 */
@Component
public class DocumentsQueryTranslator {

    /**
     * A title match is a stronger signal than a body match, so it is weighted ×3. The number is
     * a starting point, not a tuned value — Day 7 measures it.
     */
    private static final String TITLE_BOOSTED = "title^3";
    private static final String CONTENT = "content";

    /** Sorting an analysed field is meaningless; {@code title.keyword} is the exact form. */
    private static final String TITLE_SORT_FIELD = "title.keyword";
    private static final String CREATED_AT = "createdAt";

    public SearchRequest toSearchRequest(String index, SearchQuery query) {
        BoolQuery.Builder bool = new BoolQuery.Builder();

        // Query context: scored.
        if (query.hasText()) {
            bool.must(clause -> clause.multiMatch(match -> match
                    .fields(TITLE_BOOSTED, CONTENT)
                    .query(query.text())));
        }

        // Filter context: not scored, cacheable.
        if (!query.categories().isEmpty()) {
            bool.filter(termsFilter("category", query.categories()));
        }
        if (!query.tags().isEmpty()) {
            bool.filter(termsFilter("tags", query.tags()));
        }
        if (query.author() != null) {
            bool.filter(clause -> clause.term(term -> term
                    .field("author")
                    .value(FieldValue.of(query.author()))));
        }
        if (query.hasDateWindow()) {
            bool.filter(dateRangeFilter(query.createdAfter(), query.createdBefore()));
        }

        SearchRequest.Builder request = new SearchRequest.Builder()
                .index(index)
                .query(root -> root.bool(bool.build()))
                .from(query.offset())
                .size(query.size())
                .sort(sortFor(query.sort()))
                // Without this OpenSearch stops counting at 10,000 and returns that as the
                // total, which reads as a real number rather than a cap.
                .trackTotalHits(track -> track.enabled(true));

        if (query.highlight()) {
            request.highlight(highlight -> highlight
                    .preTags("<em>")
                    .postTags("</em>")
                    .fields("title", field -> field)
                    .fields(CONTENT, field -> field));
        }

        return request.build();
    }

    private static Query termsFilter(String field, Set<String> values) {
        List<FieldValue> fieldValues = values.stream().map(FieldValue::of).toList();
        return Query.of(clause -> clause.terms(terms -> terms
                .field(field)
                .terms(builder -> builder.value(fieldValues))));
    }

    private static Query dateRangeFilter(Instant after, Instant before) {
        return Query.of(clause -> clause.range(range -> {
            range.field(CREATED_AT);
            if (after != null) {
                range.gte(JsonData.of(after.toString()));
            }
            if (before != null) {
                range.lte(JsonData.of(before.toString()));
            }
            return range;
        }));
    }

    private static SortOptions sortFor(SortBy sort) {
        return switch (sort) {
            case RELEVANCE -> SortOptions.of(options -> options.score(score -> score.order(SortOrder.Desc)));
            case NEWEST -> fieldSort(CREATED_AT, SortOrder.Desc);
            case OLDEST -> fieldSort(CREATED_AT, SortOrder.Asc);
            case TITLE -> fieldSort(TITLE_SORT_FIELD, SortOrder.Asc);
        };
    }

    private static SortOptions fieldSort(String field, SortOrder order) {
        return SortOptions.of(options -> options.field(spec -> spec.field(field).order(order)));
    }
}
