package com.docsearch.application;

import com.docsearch.domain.SearchDocument;
import com.docsearch.infrastructure.opensearch.OpenSearchDocumentRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Document use cases. Owns id generation and timestamp handling so no caller can
 * write a document with an inconsistent {@code createdAt}/{@code updatedAt}.
 *
 * <p>Speaks only {@link SearchDocument} — never the API's DTOs — so the REST layer
 * can change shape without touching this class. {@code ArchitectureRulesTest}
 * enforces that direction of dependency.
 */
@Service
public class DocumentService {

    private final OpenSearchDocumentRepository repository;
    private final Clock clock;

    public DocumentService(OpenSearchDocumentRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public SearchDocument create(SearchDocument document) throws IOException {
        Instant now = Instant.now(clock);
        SearchDocument toSave = new SearchDocument(
                UUID.randomUUID().toString(),
                document.title(),
                document.content(),
                document.author(),
                document.category(),
                document.tags(),
                now,
                now);
        return repository.save(toSave);
    }

    public Optional<SearchDocument> findById(String id) throws IOException {
        return repository.findById(id);
    }

    public List<SearchDocument> findAll(int limit) throws IOException {
        return repository.findAll(limit);
    }

    /** Full replacement. Returns empty when the document does not exist. */
    public Optional<SearchDocument> replace(String id, SearchDocument replacement) throws IOException {
        Optional<SearchDocument> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        SearchDocument merged = new SearchDocument(
                id,
                replacement.title(),
                replacement.content(),
                replacement.author(),
                replacement.category(),
                replacement.tags(),
                // createdAt belongs to the original document, not the request.
                existing.get().createdAt(),
                Instant.now(clock));
        return Optional.of(repository.save(merged));
    }

    /** Partial update — only non-null fields of {@code changes} are applied. */
    public Optional<SearchDocument> patch(String id, SearchDocument changes) throws IOException {
        Optional<SearchDocument> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        SearchDocument patched = existing.get().patch(
                changes.title(),
                changes.content(),
                changes.author(),
                changes.category(),
                changes.tags().isEmpty() ? null : changes.tags(),
                Instant.now(clock));
        return Optional.of(repository.save(patched));
    }

    public boolean delete(String id) throws IOException {
        return repository.deleteById(id);
    }

    public long count() throws IOException {
        return repository.count();
    }
}
