package com.docsearch.api;

import com.docsearch.application.DocumentNotFoundException;
import com.docsearch.application.DocumentService;
import com.docsearch.domain.SearchDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import(GlobalExceptionHandler.class)
class DocumentControllerTest {

    private static final Instant T0 = Instant.parse("2026-08-05T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService service;

    private static SearchDocument stored(String id) {
        return new SearchDocument(id, "OpenSearch basics", "body", "Sourabh",
                "search", List.of("opensearch"), T0, T0);
    }

    private static final String VALID_BODY = """
            {"title":"OpenSearch basics","content":"body","author":"Sourabh",
             "category":"search","tags":["opensearch"]}
            """;

    // ---------- success paths ----------

    @Test
    void createReturns201WithLocationHeader() throws Exception {
        when(service.create(any())).thenReturn(stored("abc-123"));

        mockMvc.perform(post("/api/v1/documents").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/documents/abc-123"))
                .andExpect(jsonPath("$.id").value("abc-123"));
    }

    @Test
    void findByIdReturnsTheDocument() throws Exception {
        when(service.findById("abc-123")).thenReturn(stored("abc-123"));

        mockMvc.perform(get("/api/v1/documents/abc-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("abc-123"));
    }

    @Test
    void listUsesTheDefaultLimit() throws Exception {
        when(service.findAll(20)).thenReturn(List.of(stored("a"), stored("b")));

        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void replaceReturnsTheUpdatedDocument() throws Exception {
        when(service.replace(eq("abc-123"), any())).thenReturn(stored("abc-123"));

        mockMvc.perform(put("/api/v1/documents/abc-123")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void patchAcceptsASingleField() throws Exception {
        when(service.patch(eq("abc-123"), any())).thenReturn(stored("abc-123"));

        mockMvc.perform(patch("/api/v1/documents/abc-123")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"changed\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void patchAcceptsAnEmptyBody() throws Exception {
        when(service.patch(eq("abc-123"), any())).thenReturn(stored("abc-123"));

        mockMvc.perform(patch("/api/v1/documents/abc-123")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteReturns204WithNoBody() throws Exception {
        doNothing().when(service).delete("abc-123");

        mockMvc.perform(delete("/api/v1/documents/abc-123"))
                .andExpect(status().isNoContent());
    }

    // ---------- not found, via the advice ----------

    @Test
    void aMissingDocumentBecomesAProblemDetail404() throws Exception {
        when(service.findById("nope")).thenThrow(new DocumentNotFoundException("nope"));

        mockMvc.perform(get("/api/v1/documents/nope"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title").value("Document not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.documentId").value("nope"))
                .andExpect(jsonPath("$.type").value("https://docsearch.example/problems/document-not-found"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void everyMutatingVerbReports404ForAMissingId() throws Exception {
        when(service.replace(eq("nope"), any())).thenThrow(new DocumentNotFoundException("nope"));
        when(service.patch(eq("nope"), any())).thenThrow(new DocumentNotFoundException("nope"));
        doThrow(new DocumentNotFoundException("nope")).when(service).delete("nope");

        mockMvc.perform(put("/api/v1/documents/nope")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/documents/nope")
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/documents/nope"))
                .andExpect(status().isNotFound());
    }

    // ---------- validation, via the advice ----------

    @Test
    void aMissingRequiredFieldReturns400ListingEveryProblem() throws Exception {
        mockMvc.perform(post("/api/v1/documents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tags\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.title").value("title is required"))
                .andExpect(jsonPath("$.errors.content").value("content is required"))
                .andExpect(jsonPath("$.errors.author").value("author is required"))
                .andExpect(jsonPath("$.errors.category").value("category is required"));

        verify(service, never()).create(any());
    }

    @Test
    void anInvalidCategoryIsRejectedBeforeReachingTheService() throws Exception {
        mockMvc.perform(post("/api/v1/documents").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"t","content":"c","author":"a","category":"not/allowed"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.category").exists());

        verify(service, never()).create(any());
    }

    @Test
    void anOutOfRangeLimitIsA400RatherThanBeingSilentlyClamped() throws Exception {
        // Day 3 clamped this to 100. Silently returning something other than what was
        // asked for hides caller bugs.
        mockMvc.perform(get("/api/v1/documents").param("limit", "5000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request parameter"));

        mockMvc.perform(get("/api/v1/documents").param("limit", "0"))
                .andExpect(status().isBadRequest());

        verify(service, never()).findAll(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void malformedJsonReturns400NotAStackTrace() throws Exception {
        mockMvc.perform(post("/api/v1/documents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void aWrongFieldTypeIsAlsoAMalformedBody() throws Exception {
        mockMvc.perform(post("/api/v1/documents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": {\"nested\": true}, \"content\":\"c\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"));
    }

    @Test
    void anUnexpectedFailureDoesNotLeakInternalDetail() throws Exception {
        when(service.findById("boom")).thenThrow(new IllegalStateException("connection pool exhausted"));

        mockMvc.perform(get("/api/v1/documents/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("connection pool"))));
    }
}
