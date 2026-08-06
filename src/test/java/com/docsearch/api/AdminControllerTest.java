package com.docsearch.api;

import com.docsearch.application.DocumentIndexingService;
import com.docsearch.application.ReindexService;
import com.docsearch.application.ReindexService.ReindexOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * These two endpoints are what make tolerating a failed index write defensible: drift has to
 * be visible and repairable. So the assertions are mostly about the awkward cases — an index
 * that could not be reached, and a rebuild that partly failed — since those are the states the
 * endpoints exist to report and the ones a caller acts on.
 */
@WebMvcTest(AdminController.class)
@Import(GlobalExceptionHandler.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReindexService reindex;

    @MockitoBean
    private DocumentIndexingService indexing;

    private static Map<String, Long> counts(long opensearch, long solr) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("opensearch", opensearch);
        counts.put("solr", solr);
        return counts;
    }

    // ---------- index status ----------

    @Test
    void reportsInSyncWhenEveryIndexMatchesMongo() throws Exception {
        when(reindex.documentsInSourceOfTruth()).thenReturn(10L);
        when(indexing.counts()).thenReturn(counts(10, 10));

        mockMvc.perform(get("/api/v1/admin/index-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceOfTruth").value(10))
                .andExpect(jsonPath("$.indexes.opensearch").value(10))
                .andExpect(jsonPath("$.inSync").value(true))
                .andExpect(jsonPath("$.outOfSync").isEmpty());
    }

    @Test
    void namesTheIndexThatHasFallenBehind() throws Exception {
        // The state the running system was actually in: 10 in Mongo, 0 indexed.
        when(reindex.documentsInSourceOfTruth()).thenReturn(10L);
        when(indexing.counts()).thenReturn(counts(0, 10));

        mockMvc.perform(get("/api/v1/admin/index-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inSync").value(false))
                .andExpect(jsonPath("$.outOfSync[0]").value("opensearch"))
                .andExpect(jsonPath("$.outOfSync.length()").value(1));
    }

    @Test
    void anUnreachableIndexIsReportedAsMinusOneAndCountsAsDrift() throws Exception {
        // -1 rather than 0, because "we could not ask" is a different problem from "empty"
        // and has a different fix.
        when(reindex.documentsInSourceOfTruth()).thenReturn(10L);
        when(indexing.counts()).thenReturn(counts(10, -1));

        mockMvc.perform(get("/api/v1/admin/index-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexes.solr").value(-1))
                .andExpect(jsonPath("$.inSync").value(false))
                .andExpect(jsonPath("$.outOfSync[0]").value("solr"));
    }

    // ---------- reindex ----------

    @Test
    void reindexDefaultsToLeavingExistingIndexContentInPlace() throws Exception {
        when(reindex.documentsInSourceOfTruth()).thenReturn(10L);
        when(reindex.reindexAll(false)).thenReturn(
                Map.of("opensearch", new ReindexOutcome(10, 412, null)));

        mockMvc.perform(post("/api/v1/admin/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cleared").value(false))
                .andExpect(jsonPath("$.sourceDocuments").value(10))
                .andExpect(jsonPath("$.results.opensearch.documentsIndexed").value(10))
                .andExpect(jsonPath("$.failed").isEmpty());

        // Destructive by request only: the default must never drop index content.
        verify(reindex).reindexAll(false);
    }

    @Test
    void clearTrueIsPassedThroughToTheService() throws Exception {
        when(reindex.documentsInSourceOfTruth()).thenReturn(10L);
        when(reindex.reindexAll(true)).thenReturn(
                Map.of("opensearch", new ReindexOutcome(10, 300, null)));

        mockMvc.perform(post("/api/v1/admin/reindex").param("clear", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cleared").value(true));

        verify(reindex).reindexAll(true);
    }

    @Test
    void aPartlyFailedRebuildIsStill200ButNamesTheFailure() throws Exception {
        // 200 on purpose: one engine being unreachable is not a client error, and the
        // per-index outcome is what the caller has to inspect. A blanket 500 would hide
        // that the other index rebuilt successfully.
        Map<String, ReindexOutcome> outcomes = new LinkedHashMap<>();
        outcomes.put("opensearch", new ReindexOutcome(10, 400, null));
        outcomes.put("solr", new ReindexOutcome(0, 15_002, "[solr] failed to clear the index"));

        when(reindex.documentsInSourceOfTruth()).thenReturn(10L);
        when(reindex.reindexAll(false)).thenReturn(outcomes);

        mockMvc.perform(post("/api/v1/admin/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.opensearch.documentsIndexed").value(10))
                .andExpect(jsonPath("$.results.opensearch.error").doesNotExist())
                .andExpect(jsonPath("$.results.solr.documentsIndexed").value(0))
                .andExpect(jsonPath("$.results.solr.error").value("[solr] failed to clear the index"))
                .andExpect(jsonPath("$.failed[0]").value("solr"))
                .andExpect(jsonPath("$.failed.length()").value(1));
    }

    @Test
    void aNonBooleanClearParameterIsRejectedRatherThanCoercedToFalse() throws Exception {
        // The risk being pinned is silent coercion. If an unparseable value fell back to
        // false, this would quietly run a full rebuild nobody asked for. Spring's generic
        // "Bad Request" title is fine here — it still arrives as problem+json with a
        // correlation id — but the request must not reach the service at all.
        mockMvc.perform(post("/api/v1/admin/reindex").param("clear", "yes-please"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        verify(reindex, never()).reindexAll(anyBoolean());
    }
}
