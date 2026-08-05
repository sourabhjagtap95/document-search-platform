package com.docsearch.api;

import com.docsearch.api.dto.DocumentPatchRequest;
import com.docsearch.api.dto.DocumentRequest;
import com.docsearch.api.dto.DocumentResponse;
import com.docsearch.application.DocumentService;
import com.docsearch.domain.SearchDocument;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Document CRUD against MongoDB, the source of truth.
 *
 * <p>Absence and invalid input are handled by {@code GlobalExceptionHandler}, so the
 * methods below describe the success path only — no {@code Optional} plumbing, no
 * per-method error mapping.
 *
 * <p>Writes are not reflected in the search index yet; Day 5 adds that.
 */
// No @Validated on the class: Spring 6.1+ validates constrained controller parameters
// natively and raises HandlerMethodValidationException, which carries the parameter and
// its message. @Validated would instead route through the older AOP path and produce a
// ConstraintViolationException with less context.
@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents", description = "Create, read, update and delete documents")
public class DocumentController {

    private static final int DEFAULT_LIMIT = 20;

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @Operation(summary = "Create a document",
            description = "Assigns a server-generated id and timestamps, then persists to MongoDB.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created; the Location header holds the new URI"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = {})
    })
    @PostMapping
    public ResponseEntity<DocumentResponse> create(@Valid @RequestBody DocumentRequest request) {
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
    public DocumentResponse findById(
            @Parameter(description = "Document id") @PathVariable String id) {
        return DocumentResponse.from(service.findById(id));
    }

    @Operation(summary = "List documents", description = "Newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listed"),
            @ApiResponse(responseCode = "400", description = "limit out of range", content = {})
    })
    @GetMapping
    public List<DocumentResponse> findAll(
            @Parameter(description = "Maximum documents to return")
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT)
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 100, message = "limit must be at most 100")
            int limit) {

        // Day 3 silently clamped an out-of-range limit. Now it is a 400: quietly
        // returning something other than what was asked for hides caller bugs.
        return service.findAll(limit).stream().map(DocumentResponse::from).toList();
    }

    @Operation(summary = "Replace a document",
            description = "Overwrites every field. createdAt is preserved from the stored document.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Replaced"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = {}),
            @ApiResponse(responseCode = "404", description = "No document with that id", content = {})
    })
    @PutMapping("/{id}")
    public DocumentResponse replace(@PathVariable String id,
                                    @Valid @RequestBody DocumentRequest request) {
        return DocumentResponse.from(service.replace(id, request.toDomain()));
    }

    @Operation(summary = "Partially update a document",
            description = """
                    Applies only the fields present in the body. An empty or absent tags array
                    means "leave tags alone" — use PUT to clear them.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = {}),
            @ApiResponse(responseCode = "404", description = "No document with that id", content = {})
    })
    @PatchMapping("/{id}")
    public DocumentResponse patch(@PathVariable String id,
                                  @Valid @RequestBody DocumentPatchRequest request) {
        return DocumentResponse.from(service.patch(id, request.toDomain()));
    }

    @Operation(summary = "Delete a document")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Deleted", content = {}),
            @ApiResponse(responseCode = "404", description = "No document with that id", content = {})
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
