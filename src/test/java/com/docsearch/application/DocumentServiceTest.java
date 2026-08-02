package com.docsearch.application;

import com.docsearch.domain.SearchDocument;
import com.docsearch.infrastructure.opensearch.OpenSearchDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-08-01T09:00:00Z");

    @Mock
    private OpenSearchDocumentRepository repository;

    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createAssignsAnIdAndStampsBothTimestamps() throws IOException {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        SearchDocument created = service.create(
                new SearchDocument(null, "t", "c", "a", "cat", List.of("x"), null, null));

        assertThat(created.id()).isNotBlank();
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(created.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void createIgnoresAnyClientSuppliedIdAndTimestamps() throws IOException {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        SearchDocument created = service.create(new SearchDocument(
                "client-chosen", "t", "c", "a", "cat", List.of(), EARLIER, EARLIER));

        assertThat(created.id()).isNotEqualTo("client-chosen");
        assertThat(created.createdAt()).isEqualTo(NOW);
    }

    @Test
    void replacePreservesCreatedAtAndAdvancesUpdatedAt() throws IOException {
        SearchDocument stored = new SearchDocument(
                "1", "old", "old", "old", "old", List.of("old"), EARLIER, EARLIER);
        when(repository.findById("1")).thenReturn(Optional.of(stored));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        Optional<SearchDocument> result = service.replace("1",
                new SearchDocument(null, "new", "new", "new", "new", List.of("new"), null, null));

        assertThat(result).isPresent();
        assertThat(result.get().title()).isEqualTo("new");
        assertThat(result.get().createdAt()).isEqualTo(EARLIER);
        assertThat(result.get().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void replaceReturnsEmptyAndWritesNothingWhenAbsent() throws IOException {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        Optional<SearchDocument> result = service.replace("missing",
                new SearchDocument(null, "t", "c", "a", "cat", List.of(), null, null));

        assertThat(result).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void patchAppliesOnlySuppliedFields() throws IOException {
        SearchDocument stored = new SearchDocument(
                "1", "old title", "old content", "old author", "old cat", List.of("keep"), EARLIER, EARLIER);
        when(repository.findById("1")).thenReturn(Optional.of(stored));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.patch("1", new SearchDocument(null, "new title", null, null, null, null, null, null));

        ArgumentCaptor<SearchDocument> saved = ArgumentCaptor.forClass(SearchDocument.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().title()).isEqualTo("new title");
        assertThat(saved.getValue().content()).isEqualTo("old content");
        assertThat(saved.getValue().tags()).containsExactly("keep");
        assertThat(saved.getValue().createdAt()).isEqualTo(EARLIER);
        assertThat(saved.getValue().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void patchTreatsAnEmptyTagListAsNoChange() throws IOException {
        SearchDocument stored = new SearchDocument(
                "1", "t", "c", "a", "cat", List.of("keep"), EARLIER, EARLIER);
        when(repository.findById("1")).thenReturn(Optional.of(stored));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.patch("1", new SearchDocument(null, null, null, null, null, List.of(), null, null));

        ArgumentCaptor<SearchDocument> saved = ArgumentCaptor.forClass(SearchDocument.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().tags()).containsExactly("keep");
    }

    @Test
    void patchReturnsEmptyWhenAbsent() throws IOException {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThat(service.patch("missing",
                new SearchDocument(null, "x", null, null, null, null, null, null))).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void deleteDelegatesToTheRepository() throws IOException {
        when(repository.deleteById("1")).thenReturn(true);

        assertThat(service.delete("1")).isTrue();
    }
}
