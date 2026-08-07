package com.docsearch.infrastructure.opensearch;

import com.docsearch.domain.SearchQuery;
import com.docsearch.domain.SortBy;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole point of extracting the translator is that this runs with no client and no cluster,
 * so CI — which has no datastores — can still verify the part that actually matters: which
 * clause lands in which context.
 *
 * <p>Query context is scored, filter context is not. A filter that drifts into {@code must}
 * still returns the right documents, so nothing looks broken; it just contributes meaningless
 * arithmetic to every score. These assertions are the only thing that catches that.
 */
class DocumentsQueryTranslatorTest {

    private static final String INDEX = "documents";

    private final DocumentsQueryTranslator translator = new DocumentsQueryTranslator();

    private static SearchQuery query(String text, Set<String> categories, Set<String> tags,
                                     String author, Instant after, Instant before, SortBy sort) {
        return SearchQuery.of(text, categories, tags, author, after, before, sort, 0, 20);
    }

    private BoolQuery boolQueryFor(SearchQuery query) {
        Query root = translator.toSearchRequest(INDEX, query).query();
        assertThat(root.isBool()).as("the root clause is always a bool").isTrue();
        return root.bool();
    }

    // ---------- query context ----------

    @Test
    void textBecomesAMultiMatchInMustWithTitleBoosted() {
        BoolQuery bool = boolQueryFor(query("opensearch mappings", Set.of(), Set.of(), null, null, null, null));

        assertThat(bool.must()).hasSize(1);
        var multiMatch = bool.must().get(0).multiMatch();
        assertThat(multiMatch.query()).isEqualTo("opensearch mappings");
        assertThat(multiMatch.fields()).containsExactly("title^3", "content");
    }

    @Test
    void noTextMeansNoScoringClauseAtAll() {
        BoolQuery bool = boolQueryFor(query(null, Set.of("search"), Set.of(), null, null, null, null));

        assertThat(bool.must()).isEmpty();
        assertThat(bool.filter()).hasSize(1);
    }

    // ---------- filter context ----------

    @Test
    void categoryIsAFilterNotAMatch() {
        BoolQuery bool = boolQueryFor(query("x", Set.of("search"), Set.of(), null, null, null, null));

        assertThat(bool.filter()).hasSize(1);
        assertThat(bool.filter().get(0).terms().field()).isEqualTo("category");
        assertThat(bool.must()).hasSize(1);   // only the text clause is scored
    }

    @Test
    void tagsAreAFilter() {
        BoolQuery bool = boolQueryFor(query(null, Set.of(), Set.of("mapping", "analysis"), null, null, null, null));

        assertThat(bool.filter()).hasSize(1);
        assertThat(bool.filter().get(0).terms().field()).isEqualTo("tags");
        assertThat(bool.filter().get(0).terms().terms().value()).hasSize(2);
    }

    @Test
    void authorIsAFilter() {
        BoolQuery bool = boolQueryFor(query(null, Set.of(), Set.of(), "Sourabh", null, null, null));

        assertThat(bool.filter()).hasSize(1);
        assertThat(bool.filter().get(0).term().field()).isEqualTo("author");
    }

    @Test
    void aDateWindowIsARangeFilterOnCreatedAt() {
        BoolQuery bool = boolQueryFor(query(null, Set.of(), Set.of(), null,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-31T23:59:59.999Z"), null));

        assertThat(bool.filter()).hasSize(1);
        var range = bool.filter().get(0).range();
        assertThat(range.field()).isEqualTo("createdAt");
        assertThat(range.gte()).isNotNull();
        assertThat(range.lte()).isNotNull();
    }

    @Test
    void anOpenEndedWindowSetsOnlyTheBoundItWasGiven() {
        BoolQuery bool = boolQueryFor(query(null, Set.of(), Set.of(), null,
                Instant.parse("2026-01-01T00:00:00Z"), null, null));

        var range = bool.filter().get(0).range();
        assertThat(range.gte()).isNotNull();
        assertThat(range.lte()).isNull();
    }

    @Test
    void everyFilterIsIndependentSoFourInputsMakeFourFilters() {
        BoolQuery bool = boolQueryFor(query("x", Set.of("search"), Set.of("mapping"), "Sourabh",
                Instant.parse("2026-01-01T00:00:00Z"), null, null));

        assertThat(bool.filter()).hasSize(4);
        assertThat(bool.must()).hasSize(1);
    }

    @Test
    void anEmptyQueryMatchesEverythingWithoutFailing() {
        BoolQuery bool = boolQueryFor(query(null, Set.of(), Set.of(), null, null, null, null));

        assertThat(bool.must()).isEmpty();
        assertThat(bool.filter()).isEmpty();
    }

    // ---------- sorting ----------

    @Test
    void relevanceSortsByScore() {
        List<SortOptions> sort = translator
                .toSearchRequest(INDEX, query("x", Set.of(), Set.of(), null, null, null, SortBy.RELEVANCE))
                .sort();

        assertThat(sort).hasSize(1);
        assertThat(sort.get(0).isScore()).isTrue();
    }

    @Test
    void newestAndOldestSortOnCreatedAt() {
        var newest = translator
                .toSearchRequest(INDEX, query(null, Set.of(), Set.of(), null, null, null, SortBy.NEWEST))
                .sort().get(0).field();
        var oldest = translator
                .toSearchRequest(INDEX, query(null, Set.of(), Set.of(), null, null, null, SortBy.OLDEST))
                .sort().get(0).field();

        assertThat(newest.field()).isEqualTo("createdAt");
        assertThat(newest.order().jsonValue()).isEqualTo("desc");
        assertThat(oldest.field()).isEqualTo("createdAt");
        assertThat(oldest.order().jsonValue()).isEqualTo("asc");
    }

    @Test
    void titleSortsOnTheKeywordSubFieldNotTheAnalysedField() {
        // Sorting an analysed field sorts by whichever token the engine happens to pick. The
        // .keyword sub-field exists in the Day 3 mapping precisely so this works.
        var sort = translator
                .toSearchRequest(INDEX, query(null, Set.of(), Set.of(), null, null, null, SortBy.TITLE))
                .sort().get(0).field();

        assertThat(sort.field()).isEqualTo("title.keyword");
        assertThat(sort.order().jsonValue()).isEqualTo("asc");
    }

    // ---------- paging, totals, highlighting ----------

    @Test
    void pagingBecomesFromAndSize() {
        SearchRequest request = translator.toSearchRequest(INDEX,
                SearchQuery.of("x", Set.of(), Set.of(), null, null, null, null, 3, 25));

        assertThat(request.from()).isEqualTo(75);
        assertThat(request.size()).isEqualTo(25);
    }

    @Test
    void totalsAreTrackedExactly() {
        // Without this OpenSearch stops counting at 10,000 and reports a lower bound as if it
        // were the total. Correct totals matter more than the cost at this data size; Day 8
        // revisits it.
        SearchRequest request = translator.toSearchRequest(INDEX,
                query("x", Set.of(), Set.of(), null, null, null, null));

        assertThat(request.trackTotalHits().enabled()).isTrue();
    }

    @Test
    void highlightingIsRequestedOnlyWhenThereIsText() {
        SearchRequest withText = translator.toSearchRequest(INDEX,
                query("opensearch", Set.of(), Set.of(), null, null, null, null));
        SearchRequest withoutText = translator.toSearchRequest(INDEX,
                query(null, Set.of("search"), Set.of(), null, null, null, null));

        assertThat(withText.highlight()).isNotNull();
        assertThat(withText.highlight().fields()).containsOnlyKeys("title", "content");
        assertThat(withoutText.highlight()).isNull();
    }

    @Test
    void theIndexNameIsPassedThrough() {
        assertThat(translator.toSearchRequest("other-index",
                query("x", Set.of(), Set.of(), null, null, null, null)).index())
                .containsExactly("other-index");
    }
}
