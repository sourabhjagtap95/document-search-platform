package com.docsearch.infrastructure.opensearch;

import com.docsearch.domain.SearchDocument;
import com.docsearch.port.DocumentIndexPort;
import com.docsearch.port.IndexingException;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * OpenSearch as a {@link DocumentIndexPort}. Thin adapter over
 * {@link OpenSearchDocumentRepository}, translating its failures into the port's single
 * exception type.
 *
 * <p>Both kinds, and the second is the one that is easy to miss. {@code IOException} covers
 * not reaching the cluster; {@code OpenSearchException} is unchecked and covers the cluster
 * answering with an error instead — a read-only index block, a mapping conflict, a rejected
 * bulk write. Only the checked one has to be caught to compile, and leaving it at that lets
 * every operational error escape as something {@code DocumentIndexingService} does not catch,
 * failing a request whose MongoDB write already succeeded.
 */
@Component
@ConditionalOnProperty(name = "opensearch.enabled", matchIfMissing = true)
public class OpenSearchDocumentIndexer implements DocumentIndexPort {

    private final OpenSearchDocumentRepository repository;

    public OpenSearchDocumentIndexer(OpenSearchDocumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return "opensearch";
    }

    @Override
    public void index(SearchDocument document) {
        try {
            repository.save(document);
        } catch (IOException | OpenSearchException failure) {
            throw new IndexingException(name(), "failed to index " + document.id(), failure);
        }
    }

    @Override
    public int indexAll(List<SearchDocument> documents) {
        try {
            return repository.saveAll(documents);
        } catch (IOException | OpenSearchException failure) {
            throw new IndexingException(name(), "failed to bulk index " + documents.size()
                    + " documents", failure);
        }
    }

    @Override
    public boolean delete(String id) {
        try {
            return repository.deleteById(id);
        } catch (IOException | OpenSearchException failure) {
            throw new IndexingException(name(), "failed to delete " + id, failure);
        }
    }

    @Override
    public void clear() {
        try {
            repository.deleteAll();
        } catch (IOException | OpenSearchException failure) {
            throw new IndexingException(name(), "failed to clear the index", failure);
        }
    }

    @Override
    public long count() {
        try {
            return repository.count();
        } catch (IOException | OpenSearchException failure) {
            throw new IndexingException(name(), "failed to count documents", failure);
        }
    }
}
