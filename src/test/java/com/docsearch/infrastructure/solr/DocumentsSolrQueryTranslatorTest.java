package com.docsearch.infrastructure.solr;

import com.docsearch.domain.SearchQuery;
import com.docsearch.domain.SortBy;
import org.apache.solr.client.solrj.SolrQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Solr half of the same lesson, and the reason implementing both engines was worth it:
 * {@code q} versus {@code fq} <em>is</em> query context versus filter context. Two engines,
 * designed independently, arrived at the same split — which is the strongest available argument
 * that the distinction is fundamental rather than an OpenSearch quirk.
 *
 * <p>Assertions read off {@code SolrQuery}'s parameters, so no server is involved.
 */
class DocumentsSolrQueryTranslatorTest {

    private final DocumentsSolrQueryTranslator translator = new DocumentsSolrQueryTranslator();

    private static SearchQuery query(String text, Set<String> categories, Set<String> tags,
                                     String author, Instant after, Instant before, SortBy sort) {
        return SearchQuery.of(text, categories, tags, author, after, before, sort, 0, 20);
    }

    // ---------- query context ----------

    @Test
    void textBecomesTheQWithEdismaxAndABoostedTitle() {
        SolrQuery solr = translator.toSolrQuery(
                query("opensearch mappings", Set.of(), Set.of(), null, null, null, null));

        assertThat(solr.getQuery()).isEqualTo("opensearch mappings");
        assertThat(solr.get("defType")).isEqualTo("edismax");
        assertThat(solr.get("qf")).isEqualTo("title^3 content");
    }

    @Test
    void noTextMatchesEverything() {
        SolrQuery solr = translator.toSolrQuery(
                query(null, Set.of("search"), Set.of(), null, null, null, null));

        assertThat(solr.getQuery()).isEqualTo("*:*");
    }

    // ---------- filter context ----------

    @Test
    void categoryBecomesItsOwnFilterQuery() {
        SolrQuery solr = translator.toSolrQuery(
                query("x", Set.of("search"), Set.of(), null, null, null, null));

        assertThat(solr.getFilterQueries()).containsExactly("category:(\"search\")");
    }

    @Test
    void aSingleTagBecomesItsOwnFilterQuery() {
        SolrQuery solr = translator.toSolrQuery(
                query(null, Set.of(), Set.of("mapping"), null, null, null, null));

        assertThat(solr.getFilterQueries()).containsExactly("tags:(\"mapping\")");
    }

    @Test
    void multipleTagsAreOredInsideOneFilterQuery() {
        // Asserted without depending on Set iteration order, which is not guaranteed.
        SolrQuery solr = translator.toSolrQuery(
                query(null, Set.of(), Set.of("mapping", "analysis"), null, null, null, null));

        assertThat(solr.getFilterQueries()).hasSize(1);
        assertThat(solr.getFilterQueries()[0])
                .startsWith("tags:(")
                .endsWith(")")
                .contains("\"mapping\"")
                .contains("\"analysis\"")
                .contains(" OR ");
    }

    @Test
    void authorBecomesItsOwnFilterQuery() {
        SolrQuery solr = translator.toSolrQuery(
                query(null, Set.of(), Set.of(), "Sourabh", null, null, null));

        assertThat(solr.getFilterQueries()).containsExactly("author:(\"Sourabh\")");
    }

    @Test
    void aDateWindowBecomesARangeFilterQuery() {
        SolrQuery solr = translator.toSolrQuery(query(null, Set.of(), Set.of(), null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-31T23:59:59.999Z"), null));

        assertThat(solr.getFilterQueries())
                .containsExactly("createdAt:[2026-01-01T00:00:00Z TO 2026-01-31T23:59:59.999Z]");
    }

    @Test
    void anOpenEndedWindowUsesAWildcardBound() {
        SolrQuery solr = translator.toSolrQuery(query(null, Set.of(), Set.of(), null,
                Instant.parse("2026-01-01T00:00:00Z"), null, null));

        assertThat(solr.getFilterQueries())
                .containsExactly("createdAt:[2026-01-01T00:00:00Z TO *]");
    }

    @Test
    void eachFilterIsSeparateSoSolrCanCacheThemIndependently() {
        SolrQuery solr = translator.toSolrQuery(query("x", Set.of("search"), Set.of("mapping"),
                "Sourabh", Instant.parse("2026-01-01T00:00:00Z"), null, null));

        assertThat(solr.getFilterQueries()).hasSize(4);
    }

    @Test
    void noFiltersMeansNoFilterQueries() {
        SolrQuery solr = translator.toSolrQuery(query("x", Set.of(), Set.of(), null, null, null, null));

        assertThat(solr.getFilterQueries()).isNull();
    }

    // ---------- sorting ----------

    @Test
    void relevanceSortsByScoreDescending() {
        SolrQuery solr = translator.toSolrQuery(
                query("x", Set.of(), Set.of(), null, null, null, SortBy.RELEVANCE));

        assertThat(solr.getSorts()).hasSize(1);
        assertThat(solr.getSorts().get(0).getItem()).isEqualTo("score");
        assertThat(solr.getSorts().get(0).getOrder()).isEqualTo(SolrQuery.ORDER.desc);
    }

    @Test
    void newestAndOldestSortOnCreatedAt() {
        SolrQuery newest = translator.toSolrQuery(
                query(null, Set.of(), Set.of(), null, null, null, SortBy.NEWEST));
        SolrQuery oldest = translator.toSolrQuery(
                query(null, Set.of(), Set.of(), null, null, null, SortBy.OLDEST));

        assertThat(newest.getSorts().get(0).getItem()).isEqualTo("createdAt");
        assertThat(newest.getSorts().get(0).getOrder()).isEqualTo(SolrQuery.ORDER.desc);
        assertThat(oldest.getSorts().get(0).getOrder()).isEqualTo(SolrQuery.ORDER.asc);
    }

    @Test
    void titleSortsOnTheUnanalysedCopyNotTheAnalysedField() {
        // Sorting on `title` fails outright in Solr: "can not sort on a field w/o docValues".
        SolrQuery solr = translator.toSolrQuery(
                query(null, Set.of(), Set.of(), null, null, null, SortBy.TITLE));

        assertThat(solr.getSorts().get(0).getItem()).isEqualTo("titleSort");
        assertThat(solr.getSorts().get(0).getOrder()).isEqualTo(SolrQuery.ORDER.asc);
    }

    // ---------- paging, fields, highlighting ----------

    @Test
    void pagingBecomesStartAndRows() {
        SolrQuery solr = translator.toSolrQuery(
                SearchQuery.of("x", Set.of(), Set.of(), null, null, null, null, 3, 25));

        assertThat(solr.getStart()).isEqualTo(75);
        assertThat(solr.getRows()).isEqualTo(25);
    }

    @Test
    void scoreIsRequestedExplicitlyBecauseSolrOmitsItByDefault() {
        // Unlike OpenSearch, Solr returns no score unless the field list asks for it.
        SolrQuery solr = translator.toSolrQuery(query("x", Set.of(), Set.of(), null, null, null, null));

        assertThat(solr.getFields()).isEqualTo("*,score");
    }

    @Test
    void highlightingIsRequestedOnlyWhenThereIsText() {
        SolrQuery withText = translator.toSolrQuery(
                query("opensearch", Set.of(), Set.of(), null, null, null, null));
        SolrQuery withoutText = translator.toSolrQuery(
                query(null, Set.of("search"), Set.of(), null, null, null, null));

        assertThat(withText.getBool("hl")).isTrue();
        assertThat(withText.get("hl.fl")).isEqualTo("title,content");
        assertThat(withText.get("hl.simple.pre")).isEqualTo("<em>");
        assertThat(withText.get("hl.simple.post")).isEqualTo("</em>");
        assertThat(withoutText.getBool("hl")).isNull();
    }

    @Test
    void quotesInAFilterValueAreEscapedRatherThanBreakingTheQuery() {
        SolrQuery solr = translator.toSolrQuery(
                query(null, Set.of(), Set.of(), "O\"Brien", null, null, null));

        assertThat(solr.getFilterQueries()).containsExactly("author:(\"O\\\"Brien\")");
    }
}
