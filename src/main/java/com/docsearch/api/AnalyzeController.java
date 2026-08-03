package com.docsearch.api;

import com.docsearch.api.dto.AnalyzeResponse;
import com.docsearch.api.dto.AnalyzerComparisonResponse;
import com.docsearch.application.AnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * Read-only window onto text analysis. Useful in its own right — this is the endpoint
 * to hit first when a query returns nothing you expected.
 */
@RestController
@RequestMapping("/api/v1/analyze")
@Tag(name = "Analysis", description = "See how text is broken into searchable tokens")
public class AnalyzeController {

    private final AnalysisService service;

    public AnalyzeController(AnalysisService service) {
        this.service = service;
    }

    @Operation(summary = "Analyse text with one analyzer",
            description = """
                    Shows the tokens that would be stored for this text. Pass `field` to use
                    that field's own mapped analyzer — the most reliable form when debugging,
                    since it reflects the real mapping rather than a name you supplied.""")
    @GetMapping
    public AnalyzeResponse analyze(
            @Parameter(description = "Text to analyse", example = "OpenSearch is a Distributed Search Engine")
            @RequestParam String text,

            @Parameter(description = "Analyzer name; defaults to the document text analyzer")
            @RequestParam(required = false) String analyzer,

            @Parameter(description = "Analyse using this field's mapped analyzer instead", example = "content")
            @RequestParam(required = false) String field) throws IOException {

        if (field != null && !field.isBlank()) {
            return AnalyzeResponse.of("field:" + field, text, service.analyzeAsField(text, field));
        }
        String used = analyzer == null || analyzer.isBlank() ? AnalysisService.DOCUMENT_TEXT : analyzer;
        return AnalyzeResponse.of(used, text, service.analyze(text, used));
    }

    @Operation(summary = "Compare analyzers on the same text",
            description = """
                    Runs one string through several analyzers at once. The quickest way to see
                    what a custom chain adds over the `standard` analyzer that dynamic mapping
                    would have chosen.""")
    @GetMapping("/compare")
    public List<AnalyzerComparisonResponse> compare(
            @Parameter(description = "Text to analyse", example = "OpenSearch's Distributed Analytics Engines")
            @RequestParam String text) throws IOException {

        return service.compare(text).stream()
                .map(AnalyzerComparisonResponse::from)
                .toList();
    }
}
