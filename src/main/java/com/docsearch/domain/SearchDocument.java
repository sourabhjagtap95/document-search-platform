package com.docsearch.domain;

import java.time.Instant;
import java.util.List;

/**
 * A document in the platform, as the business understands it.
 *
 * <p>Deliberately free of Spring, MongoDB and OpenSearch types so the same shape
 * can be persisted to Mongo and indexed into OpenSearch without either store's
 * annotations leaking into the core model. {@code ArchitectureRulesTest} enforces
 * this — adding a framework import here fails the build.
 */
public record SearchDocument(
        String id,
        String title,
        String content,
        String author,
        String category,
        List<String> tags,
        Instant createdAt,
        Instant updatedAt
) {

    public SearchDocument {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** A brand-new document: no id yet, both timestamps set to {@code now}. */
    public static SearchDocument create(String title, String content, String author,
                                        String category, List<String> tags, Instant now) {
        return new SearchDocument(null, title, content, author, category, tags, now, now);
    }

    public SearchDocument withId(String id) {
        return new SearchDocument(id, title, content, author, category, tags, createdAt, updatedAt);
    }

    /**
     * Returns a copy with the supplied fields replaced where non-null, preserving
     * {@code createdAt}. Used by partial updates.
     */
    public SearchDocument patch(String title, String content, String author,
                                String category, List<String> tags, Instant now) {
        return new SearchDocument(
                id,
                title == null ? this.title : title,
                content == null ? this.content : content,
                author == null ? this.author : author,
                category == null ? this.category : category,
                tags == null ? this.tags : tags,
                createdAt,
                now);
    }
}
