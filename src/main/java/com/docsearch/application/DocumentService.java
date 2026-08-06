package com.docsearch.application;

import com.docsearch.domain.SearchDocument;
import com.docsearch.infrastructure.mongo.DocumentEntity;
import com.docsearch.infrastructure.mongo.DocumentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Document use cases, backed by MongoDB — the source of truth.
 *
 * <p>Owns id generation and timestamps so no caller can persist a document with an
 * inconsistent {@code createdAt}/{@code updatedAt}. Ids are generated here as UUIDs
 * rather than letting Mongo mint an ObjectId, because the same id has to address the
 * document in the OpenSearch index too — Day 5 depends on that.
 *
 * <p>Speaks only {@link SearchDocument}; the REST layer's DTOs never reach this class.
 *
 * <p>Writes do <strong>not</strong> reach the search index yet. That asymmetry is
 * deliberate and temporary: Day 5 introduces indexing and keeps the two stores in step.
 */
@Service
public class DocumentService {

    private final DocumentRepository repository;
    private final DocumentIndexingService indexing;
    private final Clock clock;

    public DocumentService(DocumentRepository repository,
                           DocumentIndexingService indexing,
                           Clock clock) {
        this.repository = repository;
        this.indexing = indexing;
        this.clock = clock;
    }

    public SearchDocument create(SearchDocument document) {
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
        return persist(toSave);
    }

    public SearchDocument findById(String id) {
        return repository.findById(id)
                .map(DocumentEntity::toDomain)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    /** Newest first — MongoDB can order by a stored field, so listing is now sorted. */
    public List<SearchDocument> findAll(int limit) {
        return repository
                .findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(DocumentEntity::toDomain)
                .getContent();
    }

    /** Full replacement. {@code createdAt} is taken from the stored document. */
    public SearchDocument replace(String id, SearchDocument replacement) {
        SearchDocument existing = findById(id);

        return persist(new SearchDocument(
                id,
                replacement.title(),
                replacement.content(),
                replacement.author(),
                replacement.category(),
                replacement.tags(),
                existing.createdAt(),
                Instant.now(clock)));
    }

    /** Partial update — only non-null fields of {@code changes} are applied. */
    public SearchDocument patch(String id, SearchDocument changes) {
        SearchDocument existing = findById(id);

        return persist(existing.patch(
                changes.title(),
                changes.content(),
                changes.author(),
                changes.category(),
                changes.tags().isEmpty() ? null : changes.tags(),
                Instant.now(clock)));
    }

    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new DocumentNotFoundException(id);
        }
        repository.deleteById(id);
        // Order matters here too: remove from the source of truth first. The reverse would
        // leave a window where the document is gone from search but still retrievable.
        indexing.delete(id);
    }

    public long count() {
        return repository.count();
    }

    /**
     * Persists to the source of truth, then projects into the search indexes.
     *
     * <p>Always in that order. Indexing first would allow a crash to leave a document
     * searchable that does not exist, and a search result that 404s is worse than one that
     * is briefly missing. An index that fails here is logged and left behind — see
     * {@link DocumentIndexingService} for why that is not simply swallowing an error.
     */
    private SearchDocument persist(SearchDocument document) {
        SearchDocument saved = repository.save(DocumentEntity.fromDomain(document)).toDomain();
        indexing.index(saved);
        return saved;
    }
}
