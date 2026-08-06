package com.docsearch.api;

import com.docsearch.api.dto.IndexStatusResponse;
import com.docsearch.api.dto.ReindexResponse;
import com.docsearch.application.DocumentIndexingService;
import com.docsearch.application.ReindexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operating the search indexes: see whether they agree with MongoDB, and rebuild them
 * when they do not.
 *
 * <p>These exist because the write path deliberately tolerates a failed index write. Drift
 * has to be observable and repairable for that to be a safe choice rather than a silent
 * bug — see {@code DocumentIndexingService} for the reasoning.
 *
 * <p>Unauthenticated for now. Reindexing is expensive and destructive with
 * {@code clear=true}, so Day 10 puts these behind authentication before anything is exposed
 * beyond a development machine.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Search index synchronisation and repair")
public class AdminController {

    private final ReindexService reindex;
    private final DocumentIndexingService indexing;

    public AdminController(ReindexService reindex, DocumentIndexingService indexing) {
        this.reindex = reindex;
        this.indexing = indexing;
    }

    @Operation(summary = "Compare each search index against MongoDB",
            description = """
                    Reports the document count in MongoDB and in every configured index. A
                    count of -1 means the index could not be reached, which is different from
                    it being empty.""")
    @GetMapping("/index-status")
    public IndexStatusResponse indexStatus() {
        return IndexStatusResponse.of(reindex.documentsInSourceOfTruth(), indexing.counts());
    }

    @Operation(summary = "Rebuild every search index from MongoDB",
            description = """
                    Reads MongoDB in pages and reindexes everything. Each index is rebuilt
                    independently, so one unreachable engine does not block the other.

                    Pass clear=true to drop existing index content first — that is what
                    removes documents deleted from MongoDB while an index was unreachable,
                    at the cost of a window where the index is empty.""")
    @ApiResponse(responseCode = "200",
            description = "Rebuild attempted; inspect the per-index outcome for failures")
    @PostMapping("/reindex")
    public ReindexResponse reindexAll(
            @Parameter(description = "Drop existing index content before rebuilding")
            @RequestParam(defaultValue = "false") boolean clear) {

        return ReindexResponse.from(
                reindex.documentsInSourceOfTruth(), clear, reindex.reindexAll(clear));
    }
}
