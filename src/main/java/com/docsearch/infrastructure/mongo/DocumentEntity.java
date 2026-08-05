package com.docsearch.infrastructure.mongo;

import com.docsearch.domain.SearchDocument;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * MongoDB representation of a {@link SearchDocument}.
 *
 * <p>Kept separate from the domain record so Mongo's annotations stay out of the
 * core model. The mapping methods below are the only place the two shapes meet.
 *
 * <p>Index declarations here state intent; they are not created yet because
 * {@code spring.data.mongodb.auto-index-creation} is off — creating them at
 * startup would require a live Mongo connection just to load the context. Day 4
 * turns persistence on and with it index creation.
 */
@Document(collection = "documents")
public record DocumentEntity(

        @Id
        String id,

        @Field("title")
        String title,

        @Field("content")
        String content,

        @Indexed
        @Field("author")
        String author,

        @Indexed
        @Field("category")
        String category,

        @Indexed
        @Field("tags")
        List<String> tags,

        @Field("createdAt")
        Instant createdAt,

        @Field("updatedAt")
        Instant updatedAt
) {

    public static DocumentEntity fromDomain(SearchDocument document) {
        return new DocumentEntity(
                document.id(),
                document.title(),
                document.content(),
                document.author(),
                document.category(),
                document.tags(),
                document.createdAt(),
                document.updatedAt());
    }

    public SearchDocument toDomain() {
        return new SearchDocument(id, title, content, author, category, tags, createdAt, updatedAt);
    }
}
