package com.docsearch.infrastructure.mongo;

import com.docsearch.domain.SearchDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentEntityTest {

    private static final Instant T0 = Instant.parse("2026-08-02T10:00:00Z");

    @Test
    void roundTripsThroughTheMongoRepresentationWithoutLoss() {
        SearchDocument original = new SearchDocument(
                "abc", "title", "content", "author", "category", List.of("x", "y"), T0, T0);

        SearchDocument roundTripped = DocumentEntity.fromDomain(original).toDomain();

        assertThat(roundTripped).isEqualTo(original);
    }

    @Test
    void mapsEveryFieldOntoTheEntity() {
        SearchDocument original = new SearchDocument(
                "abc", "title", "content", "author", "category", List.of("x"), T0, T0);

        DocumentEntity entity = DocumentEntity.fromDomain(original);

        assertThat(entity.id()).isEqualTo("abc");
        assertThat(entity.title()).isEqualTo("title");
        assertThat(entity.content()).isEqualTo("content");
        assertThat(entity.author()).isEqualTo("author");
        assertThat(entity.category()).isEqualTo("category");
        assertThat(entity.tags()).containsExactly("x");
        assertThat(entity.createdAt()).isEqualTo(T0);
        assertThat(entity.updatedAt()).isEqualTo(T0);
    }
}
