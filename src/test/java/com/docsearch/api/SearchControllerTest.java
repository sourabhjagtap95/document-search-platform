package com.docsearch.api;

import com.docsearch.application.SearchService;
import com.docsearch.application.UnknownSearchBackendException;
import com.docsearch.domain.SearchDocument;
import com.docsearch.domain.SearchHit;
import com.docsearch.domain.SearchQuery;
import com.docsearch.domain.SearchResults;
import com.docsearch.domain.SortBy;
import com.docsearch.port.IndexingException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
@Import(GlobalExceptionHandler.class)
class SearchControllerTest {

    private static final Instant T0 = Instant.parse("2026-08-05T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService service;

    private static SearchResults oneHit() {
        SearchDocument document = new SearchDocument("abc-123", "OpenSearch basics", "body",
                "Sourabh", "search", List.of("opensearch"), T0, T0);
        return new SearchResults("opensearch", 1, 0, 20, 7,
                List.of(new SearchHit(document, 2.43, Map.of("content", List.of("a <em>match</em>")))));
    }

    private SearchQuery captureQuery() {
        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(service).search(captor.capture(), nullable(String.class));
        return captor.getValue();
    }

    // ---------- success ----------

    @Test
    void returnsHitsWithScoresAndHighlights() throws Exception {
        when(service.search(any(), nullable(String.class))).thenReturn(oneHit());

        mockMvc.perform(get("/api/v1/search").param("q", "opensearch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engine").value("opensearch"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.tookMs").value(7))
                .andExpect(jsonPath("$.hits[0].document.id").value("abc-123"))
                .andExpect(jsonPath("$.hits[0].score").value(2.43))
                .andExpect(jsonPath("$.hits[0].highlights.content[0]").value("a <em>match</em>"));
    }

    @Test
    void totalPagesRoundsUp() throws Exception {
        when(service.search(any(), nullable(String.class)))
                .thenReturn(new SearchResults("opensearch", 21, 0, 20, 1, List.of()));

        mockMvc.perform(get("/api/v1/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void anEmptyResultIsA200NotA404() throws Exception {
        // "Nothing matched" is a successful answer to a search.
        when(service.search(any(), nullable(String.class)))
                .thenReturn(new SearchResults("opensearch", 0, 0, 20, 1, List.of()));

        mockMvc.perform(get("/api/v1/search").param("q", "nothing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.hits").isEmpty())
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    // ---------- parameter binding ----------

    @Test
    void repeatedCategoryAndTagParamsBecomeSets() throws Exception {
        when(service.search(any(), nullable(String.class))).thenReturn(oneHit());

        mockMvc.perform(get("/api/v1/search")
                        .param("category", "search").param("category", "guides")
                        .param("tag", "mapping").param("tag", "analysis"))
                .andExpect(status().isOk());

        SearchQuery query = captureQuery();
        assertThat(query.categories()).containsExactlyInAnyOrder("search", "guides");
        assertThat(query.tags()).containsExactlyInAnyOrder("mapping", "analysis");
    }

    @Test
    void aBareFromDateBecomesTheStartOfThatDayInUtc() throws Exception {
        when(service.search(any(), nullable(String.class))).thenReturn(oneHit());

        mockMvc.perform(get("/api/v1/search").param("from", "2026-01-01"))
                .andExpect(status().isOk());

        assertThat(captureQuery().createdAfter()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void aBareToDateCoversTheWholeDayRatherThanEndingAtMidnight() throws Exception {
        // Ending at midnight would silently drop everything created on the day the caller asked
        // for, which reads as missing data rather than a date bug.
        when(service.search(any(), nullable(String.class))).thenReturn(oneHit());

        mockMvc.perform(get("/api/v1/search").param("to", "2026-01-31"))
                .andExpect(status().isOk());

        assertThat(captureQuery().createdBefore())
                .isEqualTo(Instant.parse("2026-01-31T23:59:59.999Z"));
    }

    @Test
    void sortIsAcceptedCaseInsensitively() throws Exception {
        when(service.search(any(), nullable(String.class))).thenReturn(oneHit());

        mockMvc.perform(get("/api/v1/search").param("q", "x").param("sort", "TITLE"))
                .andExpect(status().isOk());

        assertThat(captureQuery().sort()).isEqualTo(SortBy.TITLE);
    }

    @Test
    void theEngineParamIsPassedThroughUntouched() throws Exception {
        when(service.search(any(), nullable(String.class))).thenReturn(oneHit());

        mockMvc.perform(get("/api/v1/search").param("q", "x").param("engine", "solr"))
                .andExpect(status().isOk());

        verify(service).search(any(), org.mockito.ArgumentMatchers.eq("solr"));
    }

    @Test
    void defaultsAreAppliedWhenNothingIsSupplied() throws Exception {
        when(service.search(any(), nullable(String.class))).thenReturn(oneHit());

        mockMvc.perform(get("/api/v1/search")).andExpect(status().isOk());

        SearchQuery query = captureQuery();
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(20);
        assertThat(query.sort()).isEqualTo(SortBy.NEWEST);   // no text, so not relevance
    }

    // ---------- validation ----------

    @Test
    void anOutOfRangeSizeIsA400() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request parameter"));

        mockMvc.perform(get("/api/v1/search").param("size", "0"))
                .andExpect(status().isBadRequest());

        verify(service, never()).search(any(), nullable(String.class));
    }

    @Test
    void aNegativePageIsA400() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("page", "-1"))
                .andExpect(status().isBadRequest());

        verify(service, never()).search(any(), nullable(String.class));
    }

    @Test
    void anUnparseableDateIsA400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("from", "last-tuesday"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"));

        verify(service, never()).search(any(), nullable(String.class));
    }

    @Test
    void anUnknownSortValueIsA400() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("sort", "sideways"))
                .andExpect(status().isBadRequest());

        verify(service, never()).search(any(), nullable(String.class));
    }

    @Test
    void anInvertedDateWindowIsA400() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("from", "2026-12-31").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("from")));

        verify(service, never()).search(any(), nullable(String.class));
    }

    // ---------- failures ----------

    @Test
    void anUnknownEngineIsA400ThatNamesWhatIsAvailable() throws Exception {
        when(service.search(any(), nullable(String.class)))
                .thenThrow(new UnknownSearchBackendException("elastic", List.of("opensearch", "solr")));

        mockMvc.perform(get("/api/v1/search").param("engine", "elastic"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title").value("Unknown search backend"))
                .andExpect(jsonPath("$.requested").value("elastic"))
                .andExpect(jsonPath("$.available[0]").value("opensearch"));
    }

    @Test
    void anUnreachableEngineIsA503RatherThanEmptyResults() throws Exception {
        // No fallback to MongoDB: it has no analyzer and no scoring, so it would answer a
        // different question and present it as this one's answer.
        when(service.search(any(), nullable(String.class)))
                .thenThrow(new IndexingException("opensearch", "search failed",
                        new RuntimeException("connection refused")));

        mockMvc.perform(get("/api/v1/search").param("q", "opensearch"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title").value("Search unavailable"))
                .andExpect(jsonPath("$.type")
                        .value("https://docsearch.example/problems/search-unavailable"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("connection refused"))));
    }
}
