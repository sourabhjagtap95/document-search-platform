package com.docsearch.application;

import com.docsearch.config.SearchProperties;
import com.docsearch.domain.SearchQuery;
import com.docsearch.domain.SearchResults;
import com.docsearch.port.DocumentSearchPort;
import com.docsearch.port.IndexingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs a search against exactly one backend.
 *
 * <p><strong>The contrast with {@link DocumentIndexingService} is the point.</strong> Indexing
 * broadcasts: every configured index must receive every write, so it loops over all of them and
 * tolerates individual failures. Searching selects: two engines score documents using different
 * term statistics, so their results cannot be merged into one ranking, and there is nothing
 * useful to do with a second opinion.
 *
 * <p><strong>And a failed search is not tolerated.</strong> That inverts the write path's
 * answer, from the same principle. A failed index write is logged and left behind because
 * MongoDB still holds the document and the index can be rebuilt from it. A failed search has no
 * such fallback: MongoDB cannot answer "documents matching opensearch" — it has no analyzer, no
 * stemming, no scoring — so a substring scan would return a different, smaller, differently
 * ordered set of documents and present it as the answer. Silently wrong results are worse than
 * an error, so {@link IndexingException} propagates and the API reports 503.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final Map<String, DocumentSearchPort> backends = new LinkedHashMap<>();
    private final String configuredBackend;

    public SearchService(List<DocumentSearchPort> backends, SearchProperties properties) {
        backends.forEach(backend -> this.backends.put(backend.name(), backend));
        this.configuredBackend = properties.backend();
        log.info("Search available on: {} (default: {})", this.backends.keySet(), configuredBackend);
    }

    public List<String> availableBackends() {
        return List.copyOf(backends.keySet());
    }

    /**
     * @param engine backend named by the request, or {@code null} to use the configured default
     * @throws UnknownSearchBackendException if the request named an unavailable backend
     * @throws IndexingException             if no backend can answer, or the chosen one failed
     */
    public SearchResults search(SearchQuery query, String engine) {
        return resolve(engine).search(query);
    }

    private DocumentSearchPort resolve(String requested) {
        if (requested != null && !requested.isBlank()) {
            String name = requested.trim().toLowerCase(Locale.ROOT);
            DocumentSearchPort backend = backends.get(name);
            if (backend == null) {
                // The caller's mistake, not a fault: 400 rather than 503.
                throw new UnknownSearchBackendException(requested.trim(), availableBackends());
            }
            return backend;
        }

        DocumentSearchPort backend = backends.get(configuredBackend);
        if (backend == null) {
            // Misconfiguration or every engine switched off. Not the caller's fault, and there
            // is no correct answer to give, so this is unavailability.
            throw new IndexingException(configuredBackend,
                    "no search backend is available (configured: '" + configuredBackend
                            + "', available: " + availableBackends() + ")", null);
        }
        return backend;
    }
}
