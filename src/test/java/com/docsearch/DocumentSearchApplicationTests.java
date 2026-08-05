package com.docsearch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Index bootstrap and sample-data seeding run on ApplicationReadyEvent, which
// @SpringBootTest does fire. Disabled here so the context loads without a live
// OpenSearch — CI has no datastores. Day 9 adds real integration tests.
@SpringBootTest(properties = {
        "opensearch.auto-create-index=false",
        "app.sample-data.enabled=false",
        "spring.data.mongodb.auto-index-creation=false"
})
class DocumentSearchApplicationTests {

    @Test
    void contextLoads() {
        // Fails if any bean definition or configuration property is broken.
    }
}
