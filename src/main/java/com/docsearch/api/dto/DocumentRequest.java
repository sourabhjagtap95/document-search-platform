package com.docsearch.api.dto;

import com.docsearch.domain.SearchDocument;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Incoming document payload. Field-level validation arrives on Day 4.
 */
@Schema(name = "DocumentRequest", description = "Document to create or update")
public record DocumentRequest(

        @Schema(description = "Document title.", example = "Introduction to OpenSearch")
        String title,

        @Schema(description = "Full text body, the primary searchable field.",
                example = "OpenSearch is a distributed search and analytics engine.")
        String content,

        @Schema(description = "Author name.", example = "Sourabh Jagtap")
        String author,

        @Schema(description = "Single category this document belongs to.", example = "search")
        String category,

        @Schema(description = "Free-form tags.", example = "[\"opensearch\", \"tutorial\"]")
        List<String> tags
) {

    public SearchDocument toDomain() {
        return new SearchDocument(null, title, content, author, category, tags, null, null);
    }
}
