package com.docsearch.api.dto;

import com.docsearch.domain.SearchResults;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * One page of search results.
 *
 * <p>{@code score} is exposed deliberately. It is what makes query context visible: adding a
 * filter changes which documents come back without changing the scores of those that remain,
 * while adding a word to {@code q} changes them.
 */
@Schema(name = "SearchResponse", description = "A page of search results")
public record SearchResponse(

        @Schema(description = "Which backend answered. Scores from different engines are not "
                + "comparable — the term statistics differ.", example = "opensearch")
        String engine,

        @Schema(description = "Total matching documents, counted exactly.", example = "3")
        long total,

        @Schema(example = "0") int page,
        @Schema(example = "20") int size,
        @Schema(description = "Pages available at this size.", example = "1") int totalPages,

        @Schema(description = "Engine-reported query time — OpenSearch's took, Solr's QTime. "
                + "Excludes network and deserialisation.", example = "12")
        long tookMs,

        List<Hit> hits
) {

    @Schema(name = "SearchHit")
    public record Hit(
            DocumentResponse document,

            @Schema(description = "Relevance score. Filter clauses contribute nothing to it.",
                    example = "2.43")
            double score,

            @Schema(description = "Matched snippets per field, wrapped in <em> tags. Empty when "
                    + "the query carried no text.")
            Map<String, List<String>> highlights
    ) {
    }

    public static SearchResponse from(SearchResults results) {
        List<Hit> hits = results.hits().stream()
                .map(hit -> new Hit(
                        DocumentResponse.from(hit.document()),
                        hit.score(),
                        hit.highlights()))
                .toList();

        int totalPages = results.size() <= 0
                ? 0
                : (int) Math.ceil((double) results.total() / results.size());

        return new SearchResponse(results.engine(), results.total(), results.page(),
                results.size(), totalPages, results.tookMs(), hits);
    }
}
