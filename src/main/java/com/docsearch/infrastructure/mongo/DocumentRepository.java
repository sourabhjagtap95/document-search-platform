package com.docsearch.infrastructure.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MongoDB persistence for documents — the source of truth.
 *
 * <p>Spring Data derives the implementation from the method names, so there is nothing
 * to write here beyond the signatures. The derived queries below exist because they are
 * cheap; anything needing real query expressiveness belongs in OpenSearch.
 */
@Repository
public interface DocumentRepository extends MongoRepository<DocumentEntity, String> {

    List<DocumentEntity> findByCategory(String category);

    List<DocumentEntity> findByAuthor(String author);
}
