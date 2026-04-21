package tn.iteam.monitoring.snapshot;

import java.time.Instant;
import java.util.Map;

public record StoredSnapshot<T>(
        T data,
        boolean degraded,
        Map<String, String> freshness,
        Instant updatedAt
) {

    public static <T> StoredSnapshot<T> of(T data, boolean degraded, Map<String, String> freshness) {
        return new StoredSnapshot<>(data, degraded, freshness, Instant.now());
    }
}
