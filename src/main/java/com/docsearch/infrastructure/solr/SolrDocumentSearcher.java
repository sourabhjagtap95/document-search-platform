package com.docsearch.infrastructure.solr;

import com.docsearch.config.SolrProperties;
import com.docsearch.domain.SearchDocument;
import com.docsearch.domain.SearchHit;
import com.docsearch.domain.SearchQuery;
import com.docsearch.domain.SearchResults;
import com.docsearch.port.DocumentSearchPort;
import com.docsearch.port.IndexingException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Apache Solr as a {@link DocumentSearchPort}.
 *
 * <p>{@code SolrException} is in the catch for the same reason as everywhere else in this
 * package: it is unchecked, it is what an unreachable node throws, and leaving it out compiles
 * cleanly while letting the failure escape the adapter's contract.
 */
@Component
@ConditionalOnProperty(name = "solr.enabled", matchIfMissing = true)
public class SolrDocumentSearcher implements DocumentSearchPort {

    private final SolrClient client;
    private final DocumentsSolrQueryTranslator translator;
    private final String collection;

    public SolrDocumentSearcher(SolrClient client,
                                DocumentsSolrQueryTranslator translator,
                                SolrProperties properties) {
        this.client = client;
        this.translator = translator;
        this.collection = properties.collection();
    }

    @Override
    public String name() {
        return "solr";
    }

    @Override
    public SearchResults search(SearchQuery query) {
        SolrQuery solrQuery = translator.toSolrQuery(query);
        try {
            QueryResponse response = client.query(collection, solrQuery);
            return toResults(query, response);
        } catch (SolrServerException | IOException | SolrException failure) {
            throw new IndexingException(name(), "search failed", failure);
        }
    }

    private SearchResults toResults(SearchQuery query, QueryResponse response) {
        Map<String, Map<String, List<String>>> highlighting = response.getHighlighting();
        List<SearchHit> hits = new ArrayList<>();

        for (SolrDocument document : response.getResults()) {
            String id = string(document, "id");
            hits.add(new SearchHit(
                    toDomain(document, id),
                    score(document),
                    highlighting == null ? Map.of() : highlighting.getOrDefault(id, Map.of())));
        }

        return new SearchResults(name(), response.getResults().getNumFound(),
                query.page(), query.size(), response.getQTime(), hits);
    }

    private static SearchDocument toDomain(SolrDocument document, String id) {
        return new SearchDocument(
                id,
                string(document, "title"),
                string(document, "content"),
                string(document, "author"),
                string(document, "category"),
                strings(document, "tags"),
                instant(document, "createdAt"),
                instant(document, "updatedAt"));
    }

    private static double score(SolrDocument document) {
        Object value = document.getFieldValue("score");
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static String string(SolrDocument document, String field) {
        Object value = document.getFieldValue(field);
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> strings(SolrDocument document, String field) {
        Collection<Object> values = document.getFieldValues(field);
        return values == null ? List.of() : values.stream().map(String::valueOf).toList();
    }

    /** Solr returns dates as {@link Date}; the domain model speaks {@link Instant}. */
    private static Instant instant(SolrDocument document, String field) {
        Object value = document.getFieldValue(field);
        if (value instanceof Date date) {
            return date.toInstant();
        }
        return value == null ? null : Instant.parse(String.valueOf(value));
    }
}
