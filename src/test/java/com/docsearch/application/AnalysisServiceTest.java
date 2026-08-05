package com.docsearch.application;

import com.docsearch.domain.AnalyzedToken;
import com.docsearch.infrastructure.opensearch.OpenSearchAnalyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock
    private OpenSearchAnalyzer analyzer;

    private static List<AnalyzedToken> tokens(String... values) {
        return java.util.stream.IntStream.range(0, values.length)
                .mapToObj(i -> new AnalyzedToken(values[i], "word", i, 0, values[i].length()))
                .toList();
    }

    @Test
    void defaultsToTheDocumentTextAnalyzerWhenNoneIsNamed() throws IOException {
        when(analyzer.analyzeWithAnalyzer(eq(AnalysisService.DOCUMENT_TEXT), any())).thenReturn(tokens("a"));

        new AnalysisService(analyzer).analyze("text", null);

        verify(analyzer).analyzeWithAnalyzer(AnalysisService.DOCUMENT_TEXT, "text");
    }

    @Test
    void treatsABlankAnalyzerNameAsAbsent() throws IOException {
        when(analyzer.analyzeWithAnalyzer(eq(AnalysisService.DOCUMENT_TEXT), any())).thenReturn(tokens("a"));

        new AnalysisService(analyzer).analyze("text", "   ");

        verify(analyzer).analyzeWithAnalyzer(AnalysisService.DOCUMENT_TEXT, "text");
    }

    @Test
    void honoursAnExplicitlyNamedAnalyzer() throws IOException {
        when(analyzer.analyzeWithAnalyzer(eq("english"), any())).thenReturn(tokens("a"));

        new AnalysisService(analyzer).analyze("text", "english");

        verify(analyzer).analyzeWithAnalyzer("english", "text");
    }

    @Test
    void analysingAsAFieldUsesTheFieldsOwnMapping() throws IOException {
        when(analyzer.analyzeWithField("content", "text")).thenReturn(tokens("a", "b"));

        assertThat(new AnalysisService(analyzer).analyzeAsField("text", "content")).hasSize(2);
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void compareRunsEveryComparisonAnalyzerAndKeepsTheirOrder() throws IOException {
        when(analyzer.analyzeWithAnalyzer(any(), any())).thenReturn(tokens("x"));

        List<AnalysisService.AnalyzerComparison> result = new AnalysisService(analyzer).compare("text");

        assertThat(result).extracting(AnalysisService.AnalyzerComparison::analyzer)
                .containsExactlyElementsOf(AnalysisService.COMPARISON_ANALYZERS);
    }

    @Test
    void comparisonSetLeadsWithOurAnalyzerAndIncludesTheDynamicMappingDefault() {
        // document_text first so it reads as the subject; standard included because that is
        // what dynamic mapping used, and the comparison is the whole point.
        assertThat(AnalysisService.COMPARISON_ANALYZERS)
                .startsWith(AnalysisService.DOCUMENT_TEXT)
                .contains("standard", "keyword");
    }
}
