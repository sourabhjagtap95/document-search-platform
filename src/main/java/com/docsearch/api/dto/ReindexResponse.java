package com.docsearch.api.dto;

import com.docsearch.application.ReindexService;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(name = "ReindexResponse", description = "Outcome of rebuilding each search index")
public record ReindexResponse(

        @Schema(description = "Documents read from MongoDB.", example = "10")
        long sourceDocuments,

        @Schema(description = "Whether the existing index content was dropped first.")
        boolean cleared,

        @Schema(description = "Per-index outcome.")
        Map<String, IndexOutcome> results,

        @Schema(description = "Indexes that failed to rebuild.", example = "[]")
        List<String> failed
) {

    @Schema(name = "IndexOutcome")
    public record IndexOutcome(
            @Schema(example = "10") int documentsIndexed,
            @Schema(example = "412") long durationMs,
            @Schema(description = "Null when the rebuild succeeded.") String error) {
    }

    public static ReindexResponse from(long sourceDocuments, boolean cleared,
                                       Map<String, ReindexService.ReindexOutcome> outcomes) {
        Map<String, IndexOutcome> results = new java.util.LinkedHashMap<>();
        outcomes.forEach((name, outcome) -> results.put(name, new IndexOutcome(
                outcome.documentsIndexed(), outcome.durationMs(), outcome.error())));

        List<String> failed = outcomes.entrySet().stream()
                .filter(entry -> !entry.getValue().succeeded())
                .map(Map.Entry::getKey)
                .toList();

        return new ReindexResponse(sourceDocuments, cleared, results, failed);
    }
}
