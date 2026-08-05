package com.docsearch.api.dto;

import com.docsearch.domain.SearchDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payload for a partial update ({@code PATCH}). Every field is optional — omitting one
 * means "leave it alone".
 *
 * <p>A separate type from {@link DocumentRequest} rather than a set of validation groups:
 * the shapes genuinely differ, and the OpenAPI document is clearer for it.
 *
 * <p><strong>Why not {@code @NotBlank} here.</strong> {@code @NotBlank} fails on
 * {@code null}, so it would reject every field a caller deliberately omitted — the exact
 * opposite of what a PATCH means. {@code @Size} and {@code @Pattern} both skip
 * {@code null} and only apply to values that are present, which is the behaviour needed:
 * "if you send it, it must be valid". {@link #NOT_BLANK} is the null-tolerant equivalent
 * of a blank check.
 */
@Schema(name = "DocumentPatchRequest",
        description = "Fields to change; omit anything that should stay as it is")
public record DocumentPatchRequest(

        @Pattern(regexp = NOT_BLANK, message = "title, if supplied, must not be blank")
        @Size(max = 200, message = "title must be at most 200 characters")
        @Schema(example = "Introduction to OpenSearch")
        String title,

        @Pattern(regexp = NOT_BLANK, message = "content, if supplied, must not be blank")
        @Size(max = 50_000, message = "content must be at most 50000 characters")
        String content,

        @Pattern(regexp = NOT_BLANK, message = "author, if supplied, must not be blank")
        @Size(max = 100, message = "author must be at most 100 characters")
        String author,

        @Pattern(regexp = "[A-Za-z0-9 _-]*[A-Za-z0-9][A-Za-z0-9 _-]*",
                message = "category may contain only letters, digits, spaces, hyphens and underscores")
        @Size(max = 50, message = "category must be at most 50 characters")
        String category,

        @Size(max = 20, message = "at most 20 tags are allowed")
        @Schema(description = "Replaces the whole tag list. An empty array leaves tags unchanged.")
        List<
                @NotBlank(message = "tags must not be blank")
                @Size(max = 40, message = "each tag must be at most 40 characters")
                String> tags
) {

    /** At least one non-whitespace character. {@code (?s)} so it spans newlines. */
    static final String NOT_BLANK = "(?s).*\\S.*";

    public SearchDocument toDomain() {
        return new SearchDocument(null, title, content, author, category, tags, null, null);
    }
}
