package com.docsearch.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchDocumentTest {

    private static final Instant T0 = Instant.parse("2026-08-02T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-02T12:00:00Z");

    @Test
    void normalisesNullTagsToAnEmptyList() {
        SearchDocument document = new SearchDocument("1", "t", "c", "a", "cat", null, T0, T0);

        assertThat(document.tags()).isEmpty();
    }

    @Test
    void defendsAgainstMutationOfTheSuppliedTagList() {
        List<String> mutable = new ArrayList<>(List.of("one"));
        SearchDocument document = new SearchDocument("1", "t", "c", "a", "cat", mutable, T0, T0);

        mutable.add("two");

        assertThat(document.tags()).containsExactly("one");
        assertThatThrownBy(() -> document.tags().add("three"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void createLeavesIdUnsetAndBothTimestampsEqual() {
        SearchDocument document = SearchDocument.create("t", "c", "a", "cat", List.of("x"), T0);

        assertThat(document.id()).isNull();
        assertThat(document.createdAt()).isEqualTo(T0);
        assertThat(document.updatedAt()).isEqualTo(T0);
    }

    @Test
    void patchReplacesOnlyNonNullFieldsAndPreservesCreatedAt() {
        SearchDocument original = new SearchDocument(
                "1", "old title", "old content", "old author", "old cat", List.of("a"), T0, T0);

        SearchDocument patched = original.patch("new title", null, null, null, null, T1);

        assertThat(patched.title()).isEqualTo("new title");
        assertThat(patched.content()).isEqualTo("old content");
        assertThat(patched.author()).isEqualTo("old author");
        assertThat(patched.category()).isEqualTo("old cat");
        assertThat(patched.tags()).containsExactly("a");
        assertThat(patched.createdAt()).isEqualTo(T0);
        assertThat(patched.updatedAt()).isEqualTo(T1);
    }

    @Test
    void withIdKeepsEverythingElse() {
        SearchDocument original = SearchDocument.create("t", "c", "a", "cat", List.of("x"), T0);

        SearchDocument identified = original.withId("generated-id");

        assertThat(identified.id()).isEqualTo("generated-id");
        assertThat(identified.title()).isEqualTo("t");
        assertThat(identified.createdAt()).isEqualTo(T0);
    }
}
