package com.forgeopslabs.fluxgate.ofrep;

import java.util.List;

/**
 * Response of a bulk evaluation.
 *
 * @param flags       the evaluated flags; empty when {@code notModified}
 * @param etag        the flag-set version from the {@code ETag} header (or
 *                    {@code metadata.version}), may be {@code null}
 * @param notModified {@code true} when the edge answered {@code 304}
 */
public record OfrepBulkResult(List<OfrepFlagResult> flags, String etag, boolean notModified) {

    public OfrepBulkResult {
        flags = List.copyOf(flags);
    }

    static OfrepBulkResult notModified(String etag) {
        return new OfrepBulkResult(List.of(), etag, true);
    }
}
