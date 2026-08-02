package com.docsearch.api;

import com.docsearch.api.dto.DocumentRequest;
import com.docsearch.api.dto.DocumentResponse;
import com.docsearch.application.DocumentService;
import com.docsearch.domain.SearchDocument;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;

/**
 * Document CRUD. Backed by OpenSearch for now — Day 4 makes MongoDB the source of
 * truth and Day 5 keeps the two in sync.
 *
 * <p>Request validation and a uniform error body are Day 4's scope, so missing
 * documents simply map to 404 here and there is no {@code @ControllerAdvice} yet.
 */
@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents", description = "Create, read, update and delete documents")
public class DocumentController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @Operation(summary = "Create a document",
            description = "Assigns a server-generated id and indexes the document.")
    @ApiResponse(responseCode = "201", description = "Created; the Location header holds the new URI")
    @PostMapping
    public ResponseEntity<DocumentResponse> create(@RequestBody DocumentRequest request) throws IOException {
        SearchDocument created = service.create(request.toDomain());
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/documents/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(DocumentResponse.from(created));
    }

    @Operation(summary = "Fetch a document by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "No document with that id", content = {})
    })
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> findById(
            @Parameter(description = "Document id") @PathVariable String id) throws IOException {
        return service.findById(id)
                .map(DocumentResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "List documents",
            description = """
                    Returns documents with a match_all query, newest-first ordering not
                    guaranteed. Real querying — matching, filtering, sorting and paging —
                    is Day 6.""")
    @GetMapping
    public List<DocumentResponse> findAll(
            @Parameter(description = "Maximum documents to return (1-100)")
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) throws IOException {
        int effective = Math.clamp(limit, 1, MAX_LIMIT);
        return service.findAll(effective).stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Operation(summary = "Replace a document",
            description = "Overwrites every field. createdAt is preserved from the stored document.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Replaced"),
            @ApiResponse(responseCode = "404", description = "No document with that id", content = {})
    })
    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponse> replace(@PathVariable String id,
                                                    @RequestBody DocumentRequest request) throws IOException {
        return service.replace(id, request.toDomain())
                .map(DocumentResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Partially update a document",
            description = """
                    Applies only the fields present in the body. Note that an empty or
                    absent tags array means "leave tags alone" — use PUT to clear them.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "404", description = "No document with that id", content = {})
    })
    @PatchMapping("/{id}")
    public ResponseEntity<DocumentResponse> patch(@PathVariable String id,
                                                  @RequestBody DocumentRequest request) throws IOException {
        return service.patch(id, request.toDomain())
                .map(DocumentResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Delete a document")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted", content = {}),
            @ApiResponse(responseCode = "404", description = "No document with that id", content = {})
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) throws IOException {
        return service.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
