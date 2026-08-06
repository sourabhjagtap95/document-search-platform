package com.docsearch.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/**
 * Whether each search index agrees with MongoDB.
 *
 * <p>Exists because the write path is allowed to leave an index behind. Tolerating drift is
 * only safe if drift is visible, so this is the other half of that design decision rather
 * than a convenience endpoint.
 */
@Schema(name = "IndexStatusResponse",
        description = "Document counts in MongoDB and in each search index")
public record IndexStatusResponse(

        @Schema(description = "Documents in MongoDB, the source of truth.", example = "10")
        long sourceOfTruth,

        @Schema(description = "Documents per index. -1 means the index could not be reached.",
                example = "{\"opensearch\": 10, \"solr\": 10}")
        Map<String, Long> indexes,

        @Schema(description = "Indexes whose count differs from MongoDB.", example = "[\"solr\"]")
        List<String> outOfSync,

        @Schema(description = "True when every index matches the source of truth.")
        boolean inSync
) {

    public static IndexStatusResponse of(long sourceOfTruth, Map<String, Long> indexes) {
        List<String> drifted = indexes.entrySet().stream()
                .filter(entry -> entry.getValue() != sourceOfTruth)
                .map(Map.Entry::getKey)
                .toList();

        return new IndexStatusResponse(sourceOfTruth, indexes, drifted, drifted.isEmpty());
    }
}
