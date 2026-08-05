package com.docsearch.api;

import com.docsearch.application.AnalysisService;
import com.docsearch.domain.AnalyzedToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyzeController.class)
class AnalyzeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisService service;

    private static List<AnalyzedToken> tokens(String... values) {
        return java.util.stream.IntStream.range(0, values.length)
                .mapToObj(i -> new AnalyzedToken(values[i], "word", i, 0, values[i].length()))
                .toList();
    }

    @Test
    void returnsTokensWithACountAndAQuickReadList() throws Exception {
        when(service.analyze(any(), any())).thenReturn(tokens("opensearch", "open", "search"));

        mockMvc.perform(get("/api/v1/analyze").param("text", "OpenSearch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analyzer").value("document_text"))
                .andExpect(jsonPath("$.text").value("OpenSearch"))
                .andExpect(jsonPath("$.tokenCount").value(3))
                .andExpect(jsonPath("$.tokens[0]").value("opensearch"))
                .andExpect(jsonPath("$.detail[1].position").value(1));
    }

    @Test
    void namedAnalyzerIsReflectedInTheResponse() throws Exception {
        when(service.analyze(any(), any())).thenReturn(tokens("opensearch"));

        mockMvc.perform(get("/api/v1/analyze").param("text", "OpenSearch").param("analyzer", "english"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analyzer").value("english"));
    }

    @Test
    void fieldParameterTakesPrecedenceOverAnalyzerName() throws Exception {
        when(service.analyzeAsField(any(), any())).thenReturn(tokens("opensearch"));

        mockMvc.perform(get("/api/v1/analyze")
                        .param("text", "OpenSearch")
                        .param("analyzer", "english")
                        .param("field", "content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analyzer").value("field:content"));

        verify(service).analyzeAsField("OpenSearch", "content");
    }

    @Test
    void comparisonReturnsOneEntryPerAnalyzer() throws Exception {
        when(service.compare(any())).thenReturn(List.of(
                new AnalysisService.AnalyzerComparison("document_text", tokens("opensearch", "open", "search")),
                new AnalysisService.AnalyzerComparison("standard", tokens("opensearch"))));

        mockMvc.perform(get("/api/v1/analyze/compare").param("text", "OpenSearch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].analyzer").value("document_text"))
                .andExpect(jsonPath("$[0].tokenCount").value(3))
                .andExpect(jsonPath("$[1].tokens[0]").value("opensearch"));
    }

    @Test
    void textIsRequired() throws Exception {
        mockMvc.perform(get("/api/v1/analyze"))
                .andExpect(status().isBadRequest());
    }
}
