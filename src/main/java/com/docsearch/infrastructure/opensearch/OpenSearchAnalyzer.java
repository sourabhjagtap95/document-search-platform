package com.docsearch.infrastructure.opensearch;

import com.docsearch.config.OpenSearchProperties;
import com.docsearch.domain.AnalyzedToken;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.AnalyzeRequest;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.List;

/**
 * Thin wrapper over the OpenSearch {@code _analyze} API — the tool that answers
 * "what did the engine actually store for this text?".
 */
@Repository
public class OpenSearchAnalyzer {

    private final OpenSearchClient client;
    private final String index;

    public OpenSearchAnalyzer(OpenSearchClient client, OpenSearchProperties properties) {
        this.client = client;
        this.index = properties.documentsIndex();
    }

    /** Runs {@code text} through a named analyzer defined on the index. */
    public List<AnalyzedToken> analyzeWithAnalyzer(String analyzer, String text) throws IOException {
        AnalyzeRequest request = new AnalyzeRequest.Builder()
                .index(index)
                .analyzer(analyzer)
                .text(text)
                .build();
        return toTokens(request);
    }

    /**
     * Runs {@code text} through whatever analyzer a given field is mapped with — the
     * form to reach for when debugging, because it uses the field's real configuration
     * rather than one you have named by hand.
     */
    public List<AnalyzedToken> analyzeWithField(String field, String text) throws IOException {
        AnalyzeRequest request = new AnalyzeRequest.Builder()
                .index(index)
                .field(field)
                .text(text)
                .build();
        return toTokens(request);
    }

    private List<AnalyzedToken> toTokens(AnalyzeRequest request) throws IOException {
        return client.indices().analyze(request).tokens().stream()
                .map(token -> new AnalyzedToken(
                        token.token(),
                        token.type(),
                        (int) token.position(),
                        (int) token.startOffset(),
                        (int) token.endOffset()))
                .toList();
    }
}
