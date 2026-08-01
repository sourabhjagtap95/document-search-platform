package com.docsearch.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApplicationPropertiesTest {

    @Autowired
    private ApplicationProperties properties;

    @Test
    void bindsTheAppConfigurationBlock() {
        assertThat(properties.name()).isEqualTo("Document Search Platform");
        assertThat(properties.description()).isNotBlank();
    }

    @Test
    void resolvesVersionThroughMavenResourceFiltering() {
        // An unfiltered build would leave the literal '@project.version@' here.
        assertThat(properties.version())
                .isNotBlank()
                .doesNotContain("@")
                .matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?");
    }
}
