package com.docsearch.api.dto;

import com.docsearch.application.AnalysisService;
import com.docsearch.domain.AnalyzedToken;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "AnalyzerComparisonResponse",
        description = "One analyzer's view of the same text")
public record AnalyzerComparisonResponse(

        @Schema(example = "standard")
        String analyzer,

        @Schema(example = "5")
        int tokenCount,

        @Schema(example = "[\"opensearch's\", \"distributed\", \"analytics\", \"engines\"]")
        List<String> tokens
) {

    public static AnalyzerComparisonResponse from(AnalysisService.AnalyzerComparison comparison) {
        return new AnalyzerComparisonResponse(
                comparison.analyzer(),
                comparison.tokens().size(),
                comparison.tokens().stream().map(AnalyzedToken::token).toList());
    }
}
