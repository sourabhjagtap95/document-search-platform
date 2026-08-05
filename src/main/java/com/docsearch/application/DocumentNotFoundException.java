package com.docsearch.application;

/**
 * Thrown when an operation names a document that does not exist.
 *
 * <p>Replaces the {@code Optional}-returning signatures used up to Day 3. Returning an
 * empty {@code Optional} forced every caller to remember to map absence onto a 404;
 * throwing means the mapping lives in exactly one place — see
 * {@code GlobalExceptionHandler} — and a caller that forgets cannot silently return 200.
 */
public class DocumentNotFoundException extends RuntimeException {

    private final String documentId;

    public DocumentNotFoundException(String documentId) {
        super("No document with id " + documentId);
        this.documentId = documentId;
    }

    public String documentId() {
        return documentId;
    }
}
