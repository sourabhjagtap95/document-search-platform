package com.docsearch.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The defaulting rules are the substance here. In particular, sorting by relevance with no
 * query text ranks by scores that are all identical, which produces arbitrary order that looks
 * deliberate — so the default has to depend on whether text was supplied.
 */
class SearchQueryTest {

    private static SearchQuery query(String text, SortBy sort) {
        return SearchQuery.of(text, Set.of(), Set.of(), null, null, null, sort, 0, 20);
    }

    @Test
    void defaultsToRelevanceWhenTextIsPresent() {
        assertThat(query("opensearch", null).sort()).isEqualTo(SortBy.RELEVANCE);
    }

    @Test
    void defaultsToNewestWhenThereIsNoText() {
        // Relevance is meaningless without a query: every score is equal.
        assertThat(query(null, null).sort()).isEqualTo(SortBy.NEWEST);
    }

    @Test
    void anExplicitSortIsNeverOverridden() {
        assertThat(query("opensearch", SortBy.TITLE).sort()).isEqualTo(SortBy.TITLE);
        assertThat(query(null, SortBy.RELEVANCE).sort()).isEqualTo(SortBy.RELEVANCE);
    }

    @Test
    void blankTextCountsAsNoText() {
        assertThat(query("   ", null).hasText()).isFalse();
        assertThat(query("   ", null).text()).isNull();
        assertThat(query("   ", null).sort()).isEqualTo(SortBy.NEWEST);
    }

    @Test
    void textIsTrimmed() {
        assertThat(query("  opensearch  ", null).text()).isEqualTo("opensearch");
    }

    @Test
    void highlightingIsOnExactlyWhenThereIsText() {
        // There is nothing to highlight without a query, so this is derived rather than a param.
        assertThat(query("opensearch", null).highlight()).isTrue();
        assertThat(query(null, null).highlight()).isFalse();
    }

    @Test
    void nullCollectionsBecomeEmptyRatherThanNull() {
        SearchQuery q = SearchQuery.of("x", null, null, null, null, null, null, 0, 20);

        assertThat(q.categories()).isEmpty();
        assertThat(q.tags()).isEmpty();
    }

    @Test
    void offsetIsPageTimesSize() {
        assertThat(SearchQuery.of("x", Set.of(), Set.of(), null, null, null, null, 3, 20).offset())
                .isEqualTo(60);
    }

    @Test
    void keepsTheDateWindowItWasGiven() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59.999Z");

        SearchQuery q = SearchQuery.of("x", Set.of(), Set.of(), null, from, to, null, 0, 20);

        assertThat(q.createdAfter()).isEqualTo(from);
        assertThat(q.createdBefore()).isEqualTo(to);
    }

    @Test
    void blankAuthorCountsAsAbsent() {
        assertThat(SearchQuery.of("x", Set.of(), Set.of(), "  ", null, null, null, 0, 20).author())
                .isNull();
    }

    @Test
    void canonicalConstructorGuardsNullAndAliasedCollections() {
        // The canonical constructor, not of(), is under test here: calling it directly is the
        // only way to bypass the factory's own null handling and prove the record guards itself.
        SearchQuery nullCollections = new SearchQuery(
                "x", null, null, null, null, null, SortBy.NEWEST, 0, 20, false);

        assertThat(nullCollections.categories()).isEmpty();
        assertThat(nullCollections.tags()).isEmpty();

        HashSet<String> mutableCategories = new HashSet<>(Set.of("news"));
        HashSet<String> mutableTags = new HashSet<>(Set.of("java"));
        SearchQuery q = new SearchQuery(
                "x", mutableCategories, mutableTags, null, null, null, SortBy.NEWEST, 0, 20, false);

        mutableCategories.add("sports");
        mutableTags.add("kotlin");

        assertThat(q.categories()).containsExactly("news");
        assertThat(q.tags()).containsExactly("java");
    }
}
