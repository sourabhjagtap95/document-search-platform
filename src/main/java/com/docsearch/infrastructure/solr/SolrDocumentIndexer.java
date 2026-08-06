package com.docsearch.infrastructure.solr;

import com.docsearch.config.SolrProperties;
import com.docsearch.domain.SearchDocument;
import com.docsearch.port.DocumentIndexPort;
import com.docsearch.port.IndexingException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Apache Solr as a {@link DocumentIndexPort}.
 *
 * <p>Every write commits. Solr, like OpenSearch, buffers writes and only makes them
 * searchable at a commit, so without this a document would be stored and unfindable. It is
 * the same trade-off as OpenSearch's {@code refresh=true} and carries the same cost — fine
 * for CRUD, wrong for bulk ingestion, which is why {@link #indexAll} sends the whole batch
 * before committing once.
 *
 * <p><strong>Every failure must leave here as an {@link IndexingException}.</strong> That is
 * the contract {@code DocumentIndexingService} relies on — it catches that type and nothing
 * else — so an exception in any other shape propagates out of the service, past the
 * per-index isolation, and fails a request whose MongoDB write already succeeded. Which is
 * the exact outcome the failure model exists to prevent.
 *
 * <p>Hence {@code SolrException} in each catch alongside the checked pair. It is unchecked,
 * and it is what SolrJ actually throws when no node is reachable ("Could not find a healthy
 * node to handle the request") or when ZooKeeper cannot be reached — the common case, not an
 * exotic one. Catching only the checked exceptions compiled cleanly and turned a stopped
 * Solr into a 500 on every write.
 */
@Component
@ConditionalOnProperty(name = "solr.enabled", matchIfMissing = true)
public class SolrDocumentIndexer implements DocumentIndexPort {

    private static final Logger log = LoggerFactory.getLogger(SolrDocumentIndexer.class);

    private final SolrClient client;
    private final String collection;

    public SolrDocumentIndexer(SolrClient client, SolrProperties properties) {
        this.client = client;
        this.collection = properties.collection();
    }

    @Override
    public String name() {
        return "solr";
    }

    @Override
    public void index(SearchDocument document) {
        try {
            client.add(collection, toSolrDocument(document));
            client.commit(collection);
            log.debug("Indexed document {} into Solr", document.id());
        } catch (SolrServerException | IOException | SolrException failure) {
            throw new IndexingException(name(), "failed to index " + document.id(), failure);
        }
    }

    @Override
    public int indexAll(List<SearchDocument> documents) {
        if (documents.isEmpty()) {
            return 0;
        }
        try {
            List<SolrInputDocument> batch = new ArrayList<>(documents.size());
            documents.forEach(document -> batch.add(toSolrDocument(document)));

            UpdateResponse response = client.add(collection, batch);
            client.commit(collection);

            if (response.getStatus() != 0) {
                throw new IndexingException(name(),
                        "bulk add returned status " + response.getStatus(), null);
            }
            return documents.size();
        } catch (SolrServerException | IOException | SolrException failure) {
            throw new IndexingException(name(), "failed to bulk index " + documents.size()
                    + " documents", failure);
        }
    }

    @Override
    public boolean delete(String id) {
        try {
            // Solr's delete is idempotent and reports success whether or not the id was
            // present, so existence is checked first to keep the port's contract honest.
            boolean existed = client.getById(collection, id) != null;
            client.deleteById(collection, id);
            client.commit(collection);
            return existed;
        } catch (SolrServerException | IOException | SolrException failure) {
            throw new IndexingException(name(), "failed to delete " + id, failure);
        }
    }

    @Override
    public void clear() {
        try {
            client.deleteByQuery(collection, "*:*");
            client.commit(collection);
        } catch (SolrServerException | IOException | SolrException failure) {
            throw new IndexingException(name(), "failed to clear the collection", failure);
        }
    }

    @Override
    public long count() {
        try {
            org.apache.solr.client.solrj.SolrQuery query =
                    new org.apache.solr.client.solrj.SolrQuery("*:*");
            query.setRows(0);
            return client.query(collection, query).getResults().getNumFound();
        } catch (SolrServerException | IOException | SolrException failure) {
            throw new IndexingException(name(), "failed to count documents", failure);
        }
    }

    /**
     * Field names match the OpenSearch mapping rather than using Solr's dynamic-field
     * suffixes ({@code title_t}, {@code category_s}). The schema declares them explicitly —
     * see {@link SolrCollectionInitializer} — so the same document shape works against both
     * engines and neither one dictates the domain model's vocabulary.
     */
    private SolrInputDocument toSolrDocument(SearchDocument document) {
        SolrInputDocument solrDocument = new SolrInputDocument();
        solrDocument.addField("id", document.id());
        solrDocument.addField("title", document.title());
        solrDocument.addField("content", document.content());
        solrDocument.addField("author", document.author());
        solrDocument.addField("category", document.category());
        document.tags().forEach(tag -> solrDocument.addField("tags", tag));
        solrDocument.addField("createdAt", java.util.Date.from(document.createdAt()));
        solrDocument.addField("updatedAt", java.util.Date.from(document.updatedAt()));
        return solrDocument;
    }
}
