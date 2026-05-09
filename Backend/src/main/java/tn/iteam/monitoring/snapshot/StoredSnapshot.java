package tn.iteam.monitoring.snapshot;

import java.time.Instant;
import java.util.Map;

public record StoredSnapshot<T>(
        T data,
        boolean degraded,
        Map<String, String> freshness,
        Instant updatedAt
) {

    public static final String FRESHNESS_LIVE = "live";
    public static final String FRESHNESS_SNAPSHOT_FALLBACK = "snapshot_fallback";
    public static final String FRESHNESS_MEMORY_SNAPSHOT_FALLBACK = "memory_snapshot_fallback";
    public static final String FRESHNESS_DATABASE_SNAPSHOT_FALLBACK = "database_snapshot_fallback";
    public static final String FRESHNESS_SNAPSHOT_MISSING = "snapshot_missing";

    public static <T> StoredSnapshot<T> of(T data, boolean degraded, Map<String, String> freshness) {
        return new StoredSnapshot<>(data, degraded, freshness, Instant.now());
    }
}
