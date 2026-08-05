package com.docsearch.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
public class OpenSearchClientConfig {

    /**
     * Jackson 2 mapper dedicated to the OpenSearch client.
     *
     * <p>Spring Boot 4 serves the application with Jackson 3 ({@code tools.jackson}),
     * while {@code opensearch-java} is built against Jackson 2
     * ({@code com.fasterxml.jackson}). Both are on the classpath, and they are
     * different types, so this bean cannot collide with Boot's auto-configured
     * mapper — but it does have to be configured independently.
     *
     * <p>{@code WRITE_DATES_AS_TIMESTAMPS} is disabled so {@link java.time.Instant}
     * is indexed as an ISO-8601 string rather than an epoch number, which is what
     * OpenSearch's date mapping expects.
     */
    @Bean
    public ObjectMapper openSearchObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean(destroyMethod = "close")
    public OpenSearchTransport openSearchTransport(OpenSearchProperties properties,
                                                   ObjectMapper openSearchObjectMapper) {
        return ApacheHttpClient5TransportBuilder
                .builder(HttpHost.create(URI.create(properties.uri())))
                .setMapper(new JacksonJsonpMapper(openSearchObjectMapper))
                // Content compression is switched off deliberately. Spring Boot 4.1
                // manages httpclient5 5.6.1, while opensearch-java 3.5.0 is built
                // against 5.5; on 5.6.x the response path wraps the body in a
                // GZIPInputStream that OpenSearch's plain JSON reply is not, so every
                // call fails with "ZipException: Not in GZIP format".
                //
                // Pinning httpclient5 back to 5.5 would instead pair it with Boot's
                // httpcore5 5.4.2 — a combination neither project tests. Losing gzip
                // on a link that is usually same-host is the cheaper trade.
                .setHttpClientConfigCallback(HttpAsyncClientBuilder::disableContentCompression)
                .build();
    }

    @Bean
    public OpenSearchClient openSearchClient(OpenSearchTransport transport) {
        return new OpenSearchClient(transport);
    }
}
