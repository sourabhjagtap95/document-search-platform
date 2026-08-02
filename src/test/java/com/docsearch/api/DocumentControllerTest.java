package com.docsearch.api;

import com.docsearch.application.DocumentService;
import com.docsearch.domain.SearchDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
class DocumentControllerTest {

    private static final Instant T0 = Instant.parse("2026-08-02T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService service;

    private static SearchDocument stored(String id) {
        return new SearchDocument(id, "OpenSearch basics", "body", "Sourabh",
                "search", List.of("opensearch"), T0, T0);
    }

    private static final String BODY = """
            {"title":"OpenSearch basics","content":"body","author":"Sourabh",
             "category":"search","tags":["opensearch"]}
            """;

    @Test
    void createReturns201WithLocationHeader() throws Exception {
        when(service.create(any())).thenReturn(stored("abc-123"));

        mockMvc.perform(post("/api/v1/documents").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/documents/abc-123"))
                .andExpect(jsonPath("$.id").value("abc-123"))
                .andExpect(jsonPath("$.title").value("OpenSearch basics"))
                .andExpect(jsonPath("$.tags[0]").value("opensearch"));
    }

    @Test
    void findByIdReturns200WhenPresent() throws Exception {
        when(service.findById("abc-123")).thenReturn(Optional.of(stored("abc-123")));

        mockMvc.perform(get("/api/v1/documents/abc-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("abc-123"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void findByIdReturns404WhenAbsent() throws Exception {
        when(service.findById("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/documents/nope")).andExpect(status().isNotFound());
    }

    @Test
    void listUsesTheDefaultLimit() throws Exception {
        when(service.findAll(20)).thenReturn(List.of(stored("a"), stored("b")));

        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listClampsAnOversizedLimitInsteadOfRejectingIt() throws Exception {
        when(service.findAll(100)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/documents").param("limit", "5000"))
                .andExpect(status().isOk());
    }

    @Test
    void listClampsANonPositiveLimit() throws Exception {
        when(service.findAll(1)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/documents").param("limit", "0"))
                .andExpect(status().isOk());
    }

    @Test
    void replaceReturns200WhenPresentAnd404WhenNot() throws Exception {
        when(service.replace(eq("abc-123"), any())).thenReturn(Optional.of(stored("abc-123")));
        when(service.replace(eq("nope"), any())).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/v1/documents/abc-123")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("abc-123"));

        mockMvc.perform(put("/api/v1/documents/nope")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchReturns200WhenPresentAnd404WhenNot() throws Exception {
        when(service.patch(eq("abc-123"), any())).thenReturn(Optional.of(stored("abc-123")));
        when(service.patch(eq("nope"), any())).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/v1/documents/abc-123")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"changed\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/documents/nope")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"changed\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204WhenRemovedAnd404WhenAbsent() throws Exception {
        when(service.delete("abc-123")).thenReturn(true);
        when(service.delete("nope")).thenReturn(false);

        mockMvc.perform(delete("/api/v1/documents/abc-123")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/documents/nope")).andExpect(status().isNotFound());
    }
}
