package com.docsearch.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code highlights} must never leak the caller's own map or its lists: a caller passing a
 * mutable map should not be able to change a hit after it has been handed off.
 */
class SearchHitTest {

    private static SearchDocument document() {
        return SearchDocument.create("Title", "Content", "author", "category", List.of(), Instant.now());
    }

    @Test
    void nullHighlightsBecomeEmptyRatherThanNull() {
        SearchHit hit = new SearchHit(document(), 1.0, null);

        assertThat(hit.highlights()).isEmpty();
    }

    @Test
    void mutatingTheSourceMapAfterConstructionDoesNotChangeTheHit() {
        Map<String, List<String>> mutable = new HashMap<>();
        mutable.put("title", new ArrayList<>(List.of("hello")));

        SearchHit hit = new SearchHit(document(), 1.0, mutable);

        mutable.put("content", List.of("world"));

        assertThat(hit.highlights()).containsOnlyKeys("title");
    }

    @Test
    void twoArgConstructorStillDefaultsToEmptyHighlights() {
        assertThat(new SearchHit(document(), 1.0).highlights()).isEmpty();
    }
}
