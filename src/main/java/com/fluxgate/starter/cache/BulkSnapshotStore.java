package com.fluxgate.starter.cache;

import com.fluxgate.starter.BulkEvaluation;
import com.fluxgate.starter.EvaluationContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Last bulk result per context, used to send {@code If-None-Match} and to
 * answer a {@code 304 Not Modified}. Entries are revalidated with the edge on
 * every bulk call, so they need no TTL; the store is only bounded in size.
 */
public final class BulkSnapshotStore {

    private final Cache<EvaluationContext, BulkEvaluation> snapshots;

    /**
     * @param maxSize maximum number of contexts to remember
     */
    public BulkSnapshotStore(long maxSize) {
        this.snapshots = Caffeine.newBuilder().maximumSize(maxSize).build();
    }

    /**
     * @return the last versioned result for {@code context}, or {@code null}
     */
    public BulkEvaluation get(EvaluationContext context) {
        return snapshots.getIfPresent(context);
    }

    /**
     * Remembers {@code evaluation} when it carries a version.
     */
    public void put(EvaluationContext context, BulkEvaluation evaluation) {
        if (evaluation.version() != null) {
            snapshots.put(context, evaluation);
        }
    }
}
