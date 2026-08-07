package com.docsearch.api;

import com.docsearch.api.dto.SearchResponse;
import com.docsearch.application.SearchService;
import com.docsearch.domain.SearchQuery;
import com.docsearch.domain.SortBy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

/**
 * Searching the index — as opposed to {@code /api/v1/documents}, which reads MongoDB.
 *
 * <p>The split is deliberate: CRUD reads the source of truth, search reads the projection. It is
 * also why this endpoint can fail with {@code 503} while {@code GET /api/v1/documents} keeps
 * working.
 *
 * <p>Parameters are flat and typed rather than a nested query body, which forces the caller's
 * intent to be mapped onto query and filter context explicitly instead of being passed through
 * as raw DSL.
 */
@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "Query the search index")
public class SearchController {

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    @Operation(summary = "Search documents",
            description = """
                    Free text in `q` is matched against `title` (boosted ×3) and `content` and is
                    scored. Every other criterion is an exact filter and contributes nothing to
                    the score.

                    `sort` defaults to `relevance` when `q` is present and to `newest` when it is
                    not, because ranking by identical scores is arbitrary order.

                    `engine` overrides the configured backend so the two can be compared. Their
                    scores are not comparable — only their result sets are.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Results, possibly empty"),
            @ApiResponse(responseCode = "400", description = "Invalid parameter or unknown engine"),
            @ApiResponse(responseCode = "503", description = "The search backend is unreachable")
    })
    @GetMapping
    public SearchResponse search(
            @Parameter(description = "Free-text query, matched against title and content")
            @RequestParam(required = false) @Size(max = 512) String q,

            @Parameter(description = "Exact category; repeatable, any match")
            @RequestParam(required = false) @Size(max = 10) Set<String> category,

            @Parameter(description = "Exact tag; repeatable, any match")
            @RequestParam(required = false) @Size(max = 10) Set<String> tag,

            @Parameter(description = "Exact author")
            @RequestParam(required = false) String author,

            @Parameter(description = "Created on or after this date (inclusive), yyyy-MM-dd")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @Parameter(description = "Created on or before this date (inclusive), yyyy-MM-dd")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,

            @Parameter(description = "relevance | newest | oldest | title")
            @RequestParam(required = false) SortBy sort,

            @Parameter(description = "Zero-based page number")
            @RequestParam(defaultValue = "0") @Min(0) int page,

            @Parameter(description = "Page size, 1–100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,

            @Parameter(description = "Backend to query; defaults to search.backend")
            @RequestParam(required = false) String engine) {

        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }

        SearchQuery query = SearchQuery.of(q, category, tag, author,
                startOfDay(from), endOfDay(to), sort, page, size);

        return SearchResponse.from(service.search(query, engine));
    }

    private static Instant startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /**
     * The end of the day, not its start. Treating a bare {@code to} as midnight would exclude
     * everything created on the day the caller asked for — which looks like missing data rather
     * than a date-handling bug.
     *
     * <p>Millisecond precision rather than {@code LocalTime.MAX}: that is the resolution both
     * engines store, so a nanosecond bound would only be rounded away.
     */
    private static Instant endOfDay(LocalDate date) {
        return date == null ? null : date.atTime(23, 59, 59, 999_000_000).toInstant(ZoneOffset.UTC);
    }
}
