package com.docsearch.api.dto;

import com.docsearch.domain.SearchDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payload for creating a document ({@code POST}) or replacing one ({@code PUT}).
 *
 * <p>Every field is required, because both operations define the whole document. The
 * partial-update shape is {@link DocumentPatchRequest}, which cannot reuse these
 * constraints — {@code @NotBlank} on a field a caller deliberately omitted would reject
 * a perfectly valid PATCH.
 */
@Schema(name = "DocumentRequest", description = "Complete document to create or replace")
public record DocumentRequest(

        @NotBlank(message = "title is required")
        @Size(max = 200, message = "title must be at most 200 characters")
        @Schema(description = "Document title.", example = "Introduction to OpenSearch",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @NotBlank(message = "content is required")
        @Size(max = 50_000, message = "content must be at most 50000 characters")
        @Schema(description = "Full text body, the primary searchable field.",
                example = "OpenSearch is a distributed search and analytics engine.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String content,

        @NotBlank(message = "author is required")
        @Size(max = 100, message = "author must be at most 100 characters")
        @Schema(description = "Author name.", example = "Sourabh Jagtap",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String author,

        @NotBlank(message = "category is required")
        @Size(max = 50, message = "category must be at most 50 characters")
        @Pattern(regexp = "[A-Za-z0-9 _-]+",
                message = "category may contain only letters, digits, spaces, hyphens and underscores")
        @Schema(description = "Single category this document belongs to.", example = "search",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String category,

        @Size(max = 20, message = "at most 20 tags are allowed")
        @Schema(description = "Free-form tags.", example = "[\"opensearch\", \"tutorial\"]")
        List<
                @NotBlank(message = "tags must not be blank")
                @Size(max = 40, message = "each tag must be at most 40 characters")
                String> tags
) {

    public SearchDocument toDomain() {
        return new SearchDocument(null, title, content, author, category, tags, null, null);
    }
}
