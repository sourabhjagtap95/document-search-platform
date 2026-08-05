package com.docsearch.application;

import com.docsearch.domain.SearchDocument;
import com.docsearch.infrastructure.mongo.DocumentEntity;
import com.docsearch.infrastructure.mongo.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-08-01T09:00:00Z");

    @Mock
    private DocumentRepository repository;

    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static DocumentEntity storedEntity() {
        return DocumentEntity.fromDomain(new SearchDocument(
                "1", "old", "old", "old", "old", List.of("old"), EARLIER, EARLIER));
    }

    private void echoSave() {
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createAssignsAnIdAndStampsBothTimestamps() {
        echoSave();

        SearchDocument created = service.create(
                new SearchDocument(null, "t", "c", "a", "cat", List.of("x"), null, null));

        assertThat(created.id()).isNotBlank();
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(created.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void createIgnoresAnyClientSuppliedIdAndTimestamps() {
        echoSave();

        SearchDocument created = service.create(new SearchDocument(
                "client-chosen", "t", "c", "a", "cat", List.of(), EARLIER, EARLIER));

        assertThat(created.id()).isNotEqualTo("client-chosen");
        assertThat(created.createdAt()).isEqualTo(NOW);
    }

    @Test
    void createGeneratesIdsRatherThanLettingMongoMintThem() {
        // The same id has to address the document in the search index too, which Day 5
        // depends on — so it cannot be a Mongo-side ObjectId.
        echoSave();
        ArgumentCaptor<DocumentEntity> saved = ArgumentCaptor.forClass(DocumentEntity.class);

        service.create(new SearchDocument(null, "t", "c", "a", "cat", List.of(), null, null));

        verify(repository).save(saved.capture());
        assertThat(saved.getValue().id()).isNotNull();
    }

    @Test
    void findByIdReturnsTheStoredDocument() {
        when(repository.findById("1")).thenReturn(Optional.of(storedEntity()));

        assertThat(service.findById("1").title()).isEqualTo("old");
    }

    @Test
    void findByIdThrowsWhenAbsent() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("missing"))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void findAllRequestsNewestFirst() {
        when(repository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(storedEntity())));

        service.findAll(5);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(page.capture());
        assertThat(page.getValue()).isEqualTo(
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Test
    void replacePreservesCreatedAtAndAdvancesUpdatedAt() {
        when(repository.findById("1")).thenReturn(Optional.of(storedEntity()));
        echoSave();

        SearchDocument result = service.replace("1",
                new SearchDocument(null, "new", "new", "new", "new", List.of("new"), null, null));

        assertThat(result.title()).isEqualTo("new");
        assertThat(result.createdAt()).isEqualTo(EARLIER);
        assertThat(result.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void replaceThrowsAndWritesNothingWhenAbsent() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replace("missing",
                new SearchDocument(null, "t", "c", "a", "cat", List.of(), null, null)))
                .isInstanceOf(DocumentNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void patchAppliesOnlySuppliedFields() {
        when(repository.findById("1")).thenReturn(Optional.of(storedEntity()));
        echoSave();

        service.patch("1", new SearchDocument(null, "new title", null, null, null, null, null, null));

        ArgumentCaptor<DocumentEntity> saved = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().title()).isEqualTo("new title");
        assertThat(saved.getValue().content()).isEqualTo("old");
        assertThat(saved.getValue().tags()).containsExactly("old");
        assertThat(saved.getValue().createdAt()).isEqualTo(EARLIER);
        assertThat(saved.getValue().updatedAt()).isEqualTo(NOW);
    }

    @Test
    void patchTreatsAnEmptyTagListAsNoChange() {
        when(repository.findById("1")).thenReturn(Optional.of(storedEntity()));
        echoSave();

        service.patch("1", new SearchDocument(null, null, null, null, null, List.of(), null, null));

        ArgumentCaptor<DocumentEntity> saved = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().tags()).containsExactly("old");
    }

    @Test
    void patchThrowsWhenAbsent() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.patch("missing",
                new SearchDocument(null, "x", null, null, null, null, null, null)))
                .isInstanceOf(DocumentNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deleteChecksExistenceFirstSoAMissingIdIsNotSilentlyAccepted() {
        when(repository.existsById("1")).thenReturn(true);

        service.delete("1");

        verify(repository).deleteById("1");
    }

    @Test
    void deleteThrowsWhenAbsentAndRemovesNothing() {
        when(repository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> service.delete("missing"))
                .isInstanceOf(DocumentNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }
}
