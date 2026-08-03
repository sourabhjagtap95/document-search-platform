package com.docsearch.application;

import com.docsearch.domain.AnalyzedToken;
import com.docsearch.infrastructure.opensearch.OpenSearchAnalyzer;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * Exposes analysis so the effect of the mapping can be seen rather than assumed.
 *
 * <p>Analysis happens twice: once when a document is indexed, and again on the query
 * text at search time. Both sides must agree, so most "my search returns nothing"
 * problems are answered by running the same string through here.
 */
@Service
public class AnalysisService {

    /** The analyzer configured for the {@code title} and {@code content} fields. */
    public static final String DOCUMENT_TEXT = "document_text";

    /**
     * Built-in analyzers worth comparing against, to show what the custom chain adds.
     * {@code standard} is what dynamic mapping would have used; {@code keyword} emits
     * the whole input as a single token, which is what a keyword field does.
     */
    public static final List<String> COMPARISON_ANALYZERS =
            List.of(DOCUMENT_TEXT, "standard", "simple", "english", "keyword", "whitespace");

    private final OpenSearchAnalyzer analyzer;

    public AnalysisService(OpenSearchAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public List<AnalyzedToken> analyze(String text, String analyzerName) throws IOException {
        String name = analyzerName == null || analyzerName.isBlank() ? DOCUMENT_TEXT : analyzerName;
        return analyzer.analyzeWithAnalyzer(name, text);
    }

    /** Analyses using a field's own mapped analyzer — the debugging form. */
    public List<AnalyzedToken> analyzeAsField(String text, String field) throws IOException {
        return analyzer.analyzeWithField(field, text);
    }

    /**
     * Runs one string through several analyzers so the differences are visible
     * side by side.
     */
    public List<AnalyzerComparison> compare(String text) throws IOException {
        List<AnalyzerComparison> results = new java.util.ArrayList<>();
        for (String name : COMPARISON_ANALYZERS) {
            results.add(new AnalyzerComparison(name, analyzer.analyzeWithAnalyzer(name, text)));
        }
        return results;
    }

    public record AnalyzerComparison(String analyzer, List<AnalyzedToken> tokens) {
    }
}
