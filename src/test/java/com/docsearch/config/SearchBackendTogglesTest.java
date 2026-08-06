package com.docsearch.config;

import com.docsearch.application.DocumentIndexingService;
import com.docsearch.infrastructure.solr.SolrCollectionInitializer;
import com.docsearch.infrastructure.solr.SolrDocumentIndexer;
import org.apache.solr.client.solrj.SolrClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Either search backend can be switched off, and switching one off must leave a context that
 * still starts. {@code solr.enabled=false} is what CI and anyone without a ZooKeeper ensemble
 * relies on, so the whole Solr side — client, indexer and collection initializer — has to
 * disappear together. Gating only some of it leaves beans asking for a client nobody built.
 */
@SpringBootTest(properties = {
        "solr.enabled=false",
        "opensearch.auto-create-index=false",
        "app.sample-data.enabled=false",
        "spring.data.mongodb.auto-index-creation=false"
})
class SearchBackendTogglesTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DocumentIndexingService indexing;

    @Test
    void disablingSolrRemovesEveryBeanThatNeedsASolrClient() {
        assertThat(context.getBeansOfType(SolrClient.class)).isEmpty();
        assertThat(context.getBeansOfType(SolrDocumentIndexer.class)).isEmpty();
        assertThat(context.getBeansOfType(SolrCollectionInitializer.class)).isEmpty();
    }

    @Test
    void theRemainingBackendStillReceivesDocuments() {
        // The point of the toggle: OpenSearch keeps working on its own.
        assertThat(indexing.names()).containsExactly("opensearch");
    }
}
