package com.docsearch.application;

import com.docsearch.domain.SearchDocument;
import com.docsearch.infrastructure.mongo.DocumentRepository;
import com.docsearch.port.DocumentIndexPort;
import com.docsearch.port.IndexingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds every search index from MongoDB.
 *
 * <p>This is the counterpart to {@link DocumentIndexingService} tolerating a failed index
 * write: because the index is a projection of the source of truth, it can always be
 * discarded and rebuilt. That property is what makes an index allowed to lag — and it is
 * why the source of truth must never be the thing that lags.
 *
 * <p>Reads MongoDB in pages rather than all at once, so a large collection cannot exhaust
 * the heap. Each index is rebuilt independently, so one unreachable engine does not prevent
 * the other from being repaired.
 */
@Service
public class ReindexService {

    private static final Logger log = LoggerFactory.getLogger(ReindexService.class);
    private static final int PAGE_SIZE = 500;

    private final DocumentRepository repository;
    private final DocumentIndexingService indexing;

    public ReindexService(DocumentRepository repository, DocumentIndexingService indexing) {
        this.repository = repository;
        this.indexing = indexing;
    }

    /**
     * @param clearFirst drop existing index content before rebuilding. Removes documents
     *                   deleted from MongoDB while an index was unreachable, at the cost of
     *                   a window where the index is empty.
     */
    public Map<String, ReindexOutcome> reindexAll(boolean clearFirst) {
        Map<String, ReindexOutcome> outcomes = new LinkedHashMap<>();

        for (DocumentIndexPort port : indexing.indexes()) {
            outcomes.put(port.name(), reindexOne(port, clearFirst));
        }
        return outcomes;
    }

    private ReindexOutcome reindexOne(DocumentIndexPort port, boolean clearFirst) {
        long started = System.nanoTime();
        try {
            if (clearFirst) {
                port.clear();
            }

            int indexed = 0;
            int page = 0;
            List<SearchDocument> batch;
            do {
                batch = readPage(page++);
                if (!batch.isEmpty()) {
                    indexed += port.indexAll(batch);
                }
            } while (batch.size() == PAGE_SIZE);

            long millis = (System.nanoTime() - started) / 1_000_000;
            log.info("Reindexed {} documents into {} in {}ms", indexed, port.name(), millis);
            return new ReindexOutcome(indexed, millis, null);

        } catch (IndexingException failure) {
            long millis = (System.nanoTime() - started) / 1_000_000;
            log.error("Reindex of {} failed", port.name(), failure);
            return new ReindexOutcome(0, millis, failure.getMessage());
        }
    }

    private List<SearchDocument> readPage(int page) {
        List<SearchDocument> documents = new ArrayList<>(PAGE_SIZE);
        repository.findAll(PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.ASC, "createdAt")))
                .forEach(entity -> documents.add(entity.toDomain()));
        return documents;
    }

    /** @param error {@code null} when the rebuild succeeded */
    public record ReindexOutcome(int documentsIndexed, long durationMs, String error) {

        public boolean succeeded() {
            return error == null;
        }
    }

    /** Documents in MongoDB, for comparison against each index's count. */
    public long documentsInSourceOfTruth() {
        return repository.count();
    }
}
