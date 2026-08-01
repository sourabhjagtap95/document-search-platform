package com.docsearch.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the generated OpenAPI document. Without this, a broken springdoc upgrade
 * or a renamed path would only surface by someone opening Swagger UI by hand.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesAnOpenApi3Document() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value(org.hamcrest.Matchers.startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("Document Search Platform API"))
                .andExpect(jsonPath("$.info.version").value("v1"));
    }

    @Test
    void documentsTheHealthEndpointWithItsResponseSchema() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/health'].get.summary").value("Application liveness"))
                .andExpect(jsonPath("$.paths['/api/v1/health'].get.tags[0]").value("Health"))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/health'].get.responses.200.content['application/json'].schema.$ref")
                        .value("#/components/schemas/HealthResponse"))
                .andExpect(jsonPath("$.components.schemas.HealthResponse.properties.status.example").value("UP"));
    }

    @Test
    void excludesActuatorFromThePublicContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/actuator/health']").doesNotExist());
    }

    @Test
    void servesTheOpenApiDocumentAsYamlToo() throws Exception {
        mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk());
    }

    @Test
    void servesSwaggerUi() throws Exception {
        // springdoc redirects /swagger-ui.html to the bundled index page.
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }
}
