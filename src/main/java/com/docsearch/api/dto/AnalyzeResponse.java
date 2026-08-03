package com.docsearch.api.dto;

import com.docsearch.domain.AnalyzedToken;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "AnalyzeResponse", description = "Tokens produced for a piece of text")
public record AnalyzeResponse(

        @Schema(description = "Analyzer that produced these tokens.", example = "document_text")
        String analyzer,

        @Schema(description = "The text that was analysed.")
        String text,

        @Schema(description = "Token count.", example = "6")
        int tokenCount,

        @Schema(description = "Just the token strings, in order — the quick read.",
                example = "[\"opensearch\", \"open\", \"search\", \"distribut\", \"search\", \"engin\"]")
        List<String> tokens,

        @Schema(description = "Full detail per token, including position and source offsets.")
        List<AnalyzedToken> detail
) {

    public static AnalyzeResponse of(String analyzer, String text, List<AnalyzedToken> tokens) {
        return new AnalyzeResponse(
                analyzer,
                text,
                tokens.size(),
                tokens.stream().map(AnalyzedToken::token).toList(),
                tokens);
    }
}
