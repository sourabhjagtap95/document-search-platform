package com.docsearch.infrastructure.opensearch;

import com.docsearch.config.OpenSearchProperties;
import com.docsearch.domain.SearchDocument;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.GetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Document CRUD against the OpenSearch document APIs: index, get, update, delete.
 *
 * <p>Until Day 4 wires MongoDB persistence, this is the only store the API writes
 * to. Day 5 makes MongoDB the source of truth and keeps this index in sync.
 *
 * <p>Writes use {@link Refresh#True} so a read immediately after a write sees the
 * change. That is the right trade-off for a CRUD API and for teaching, but it
 * forces a segment flush per write — Day 7 revisits it for bulk throughput.
 */
@Repository
public class OpenSearchDocumentRepository {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchDocumentRepository.class);

    private final OpenSearchClient client;
    private final String index;

    public OpenSearchDocumentRepository(OpenSearchClient client, OpenSearchProperties properties) {
        this.client = client;
        this.index = properties.documentsIndex();
    }

    /** Creates or fully replaces the document at {@code document.id()}. */
    public SearchDocument save(SearchDocument document) throws IOException {
        client.index(request -> request
                .index(index)
                .id(document.id())
                .document(document)
                .refresh(Refresh.True));
        log.debug("Indexed document {}", document.id());
        return document;
    }

    public Optional<SearchDocument> findById(String id) throws IOException {
        GetResponse<SearchDocument> response =
                client.get(request -> request.index(index).id(id), SearchDocument.class);
        return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();
    }

    public boolean existsById(String id) throws IOException {
        return client.exists(request -> request.index(index).id(id)).value();
    }

    /**
     * Partial update through the OpenSearch {@code _update} API — only the fields
     * present in {@code partial} are merged, the rest of the stored document is
     * left alone.
     */
    public Optional<SearchDocument> update(String id, SearchDocument partial) throws IOException {
        if (!existsById(id)) {
            return Optional.empty();
        }
        client.update(request -> request
                .index(index)
                .id(id)
                .doc(partial)
                .refresh(Refresh.True), SearchDocument.class);
        return findById(id);
    }

    public boolean deleteById(String id) throws IOException {
        Result result = client.delete(request -> request
                .index(index)
                .id(id)
                .refresh(Refresh.True)).result();
        return result == Result.Deleted;
    }

    /**
     * Lists documents with a {@code match_all} query. Real querying — matching,
     * filtering, paging and sorting — is Day 6; this exists so CRUD results and
     * the seeded sample data can be inspected.
     */
    public List<SearchDocument> findAll(int limit) throws IOException {
        return client.search(request -> request
                                .index(index)
                                .size(limit)
                                .query(query -> query.matchAll(matchAll -> matchAll)),
                        SearchDocument.class)
                .hits().hits().stream()
                .map(hit -> {
                    SearchDocument source = hit.source();
                    // _source omits the document id; graft the _id back on.
                    return source == null ? null : source.withId(hit.id());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public long count() throws IOException {
        return client.count(request -> request.index(index)).count();
    }

    /**
     * Removes every document but keeps the index, so the mapping and analyzer survive.
     * Deleting the index itself would drop the Day 3 configuration and let a later write
     * recreate it with inferred mappings.
     */
    public void deleteAll() throws IOException {
        client.deleteByQuery(request -> request
                .index(index)
                .query(query -> query.matchAll(matchAll -> matchAll))
                .refresh(Refresh.True));
    }

    /** Indexes many documents in one round trip via the bulk API. */
    public int saveAll(List<SearchDocument> documents) throws IOException {
        if (documents.isEmpty()) {
            return 0;
        }
        BulkRequest.Builder bulk = new BulkRequest.Builder().refresh(Refresh.True);
        for (SearchDocument document : documents) {
            bulk.operations(operation -> operation
                    .index(indexOp -> indexOp
                            .index(index)
                            .id(document.id())
                            .document(document)));
        }

        BulkResponse response = client.bulk(bulk.build());
        if (response.errors()) {
            response.items().stream()
                    .filter(item -> item.error() != null)
                    .forEach(item -> log.error("Bulk index failed for id {}: {}",
                            item.id(), item.error().reason()));
            throw new IOException("Bulk indexing reported failures for index " + index);
        }
        return response.items().size();
    }
}
