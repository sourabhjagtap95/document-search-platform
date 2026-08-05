package com.docsearch.api.dto;

import com.docsearch.domain.SearchDocument;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(name = "DocumentResponse", description = "A stored document")
public record DocumentResponse(

        @Schema(description = "Server-generated identifier.",
                example = "3f1c9c1e-9a5f-4b2e-8f5a-6d9c1b2a7e40")
        String id,

        @Schema(example = "Introduction to OpenSearch")
        String title,

        @Schema(example = "OpenSearch is a distributed search and analytics engine.")
        String content,

        @Schema(example = "Sourabh Jagtap")
        String author,

        @Schema(example = "search")
        String category,

        @Schema(example = "[\"opensearch\", \"tutorial\"]")
        List<String> tags,

        @Schema(description = "When the document was first created.")
        Instant createdAt,

        @Schema(description = "When the document was last modified.")
        Instant updatedAt
) {

    public static DocumentResponse from(SearchDocument document) {
        return new DocumentResponse(
                document.id(),
                document.title(),
                document.content(),
                document.author(),
                document.category(),
                document.tags(),
                document.createdAt(),
                document.updatedAt());
    }
}
