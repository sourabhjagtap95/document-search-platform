package com.docsearch.application;

import java.util.List;

/**
 * A request named a search backend that is not available.
 *
 * <p>Distinct from {@code IndexingException} because the cause is different and so is the
 * correct status: this is a client asking for something that does not exist (`400`), not
 * infrastructure failing to answer (`503`). An engine switched off with
 * {@code solr.enabled=false} lands here too — from outside, that is indistinguishable from a
 * typo, and both are the caller's problem to fix.
 */
public class UnknownSearchBackendException extends RuntimeException {

    private final String requested;
    private final List<String> available;

    public UnknownSearchBackendException(String requested, List<String> available) {
        super("Unknown search backend '" + requested + "'. Available: " + available);
        this.requested = requested;
        this.available = List.copyOf(available);
    }

    public String requested() {
        return requested;
    }

    public List<String> available() {
        return available;
    }
}
