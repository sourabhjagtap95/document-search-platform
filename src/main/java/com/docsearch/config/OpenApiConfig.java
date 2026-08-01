package com.docsearch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /**
     * Document-level OpenAPI metadata. Everything below the {@code info} block —
     * paths, schemas, response shapes — is generated from the controllers and the
     * annotations on them, so it cannot drift from the code.
     */
    @Bean
    public OpenAPI documentSearchOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Document Search Platform API")
                        // The API contract version, deliberately not the build version:
                        // the jar can be rebuilt many times without the contract changing.
                        .version("v1")
                        .description("""
                                Document search over MongoDB and OpenSearch.

                                MongoDB is the source of truth; OpenSearch is the search index.
                                Document and search endpoints are added on later days — only the
                                health endpoint exists so far."""))
                // Relative URL, so the spec stays correct behind a proxy or a different host.
                .addServersItem(new Server().url("/").description("Current host"));
    }
}
