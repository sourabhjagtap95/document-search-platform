package com.docsearch.config;

import org.apache.solr.client.solrj.impl.CloudHttp2SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnProperty(name = "solr.enabled", matchIfMissing = true)
public class SolrClientConfig {

    /**
     * Connects through ZooKeeper rather than to a Solr node.
     *
     * <p>That is the whole reason ZooKeeper is here. ZooKeeper holds the cluster state —
     * which nodes are live, which replica currently leads each shard — so the client
     * routes each request itself and keeps working through leader elections, restarts and
     * shard moves with no configuration change. Pointing at a single node instead would
     * make that node a single point of failure and an unnecessary hop.
     *
     * <p>{@code CloudHttp2SolrClient} is the current implementation;
     * {@link CloudSolrClient} remains as the older HTTP/1.1 client.
     *
     * <p>Note the connect string may carry a chroot suffix — {@code host:2181/solr} — which
     * namespaces Solr's data inside a ZooKeeper ensemble shared with other services.
     */
    @Bean(destroyMethod = "close")
    public CloudHttp2SolrClient solrClient(SolrProperties properties) {
        String zkHost = properties.zkHost();
        int chroot = zkHost.indexOf('/');

        return new CloudHttp2SolrClient.Builder(
                List.of(chroot < 0 ? zkHost : zkHost.substring(0, chroot)),
                chroot < 0 ? Optional.empty() : Optional.of(zkHost.substring(chroot)))
                .withDefaultCollection(properties.collection())
                .withZkConnectTimeout(properties.connectTimeoutMs(), TimeUnit.MILLISECONDS)
                .withZkClientTimeout((int) Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }
}
