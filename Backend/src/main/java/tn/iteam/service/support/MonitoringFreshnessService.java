package tn.iteam.service.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.iteam.monitoring.snapshot.SnapshotStore;
import tn.iteam.monitoring.snapshot.StoredSnapshot;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MonitoringFreshnessService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringFreshnessService.class);
    private final SnapshotStore snapshotStore;
    private final Map<String, Instant> lastSuccessfulFetch = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSuccessfulPersist = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastPublishTimestamp = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastPersistHash = new ConcurrentHashMap<>();
    private final Map<String, Integer> lastPublishHash = new ConcurrentHashMap<>();

    public MonitoringFreshnessService(SnapshotStore snapshotStore) {
        this.snapshotStore = snapshotStore;
    }

    public boolean shouldSkipFetch(String dataset, String source, long ttlMs) {
        if (ttlMs <= 0) {
            return false;
        }

        String key = key(dataset, source);
        Instant now = Instant.now();
        Instant lastFetch = lastSuccessfulFetch.get(key);
        Instant lastPersist = lastSuccessfulPersist.get(key);
        Instant lastPublish = lastPublishTimestamp.get(key);
        Optional<StoredSnapshot<Object>> existingSnapshot = snapshotStore.get(dataset, source);
        Instant snapshotUpdatedAt = existingSnapshot.map(StoredSnapshot::updatedAt).orElse(null);

        boolean fresh = isFresh(lastFetch, now, ttlMs)
                && isFresh(lastPersist, now, ttlMs)
                && isFresh(lastPublish, now, ttlMs)
                && isFresh(snapshotUpdatedAt, now, ttlMs);
        if (fresh) {
            log.debug("CACHE HIT dataset={} source={} ttlMs={}", dataset, source, ttlMs);
        } else {
            log.debug("CACHE MISS dataset={} source={} ttlMs={}", dataset, source, ttlMs);
        }
        return fresh;
    }

    public void markFetchSuccess(String dataset, String source) {
        lastSuccessfulFetch.put(key(dataset, source), Instant.now());
    }

    public boolean hasPersistDelta(String dataset, String source, Object payload) {
        String key = key(dataset, source);
        int newHash = Objects.hashCode(payload);
        Integer previous = lastPersistHash.get(key);
        boolean delta = previous == null || previous != newHash;
        if (delta) {
            log.debug("DELTA DETECTED stage=persist dataset={} source={}", dataset, source);
        }
        return delta;
    }

    public void markPersistSuccess(String dataset, String source, Object payload) {
        String key = key(dataset, source);
        lastSuccessfulPersist.put(key, Instant.now());
        lastPersistHash.put(key, Objects.hashCode(payload));
    }

    public boolean hasPublishDelta(String dataset, String source, Object payload) {
        String key = key(dataset, source);
        int newHash = Objects.hashCode(payload);
        Integer previous = lastPublishHash.get(key);
        boolean delta = previous == null || previous != newHash;
        if (delta) {
            log.debug("DELTA DETECTED stage=publish dataset={} source={}", dataset, source);
        }
        return delta;
    }

    public void markPublished(String dataset, String source, Object payload) {
        String key = key(dataset, source);
        lastPublishTimestamp.put(key, Instant.now());
        lastPublishHash.put(key, Objects.hashCode(payload));
    }

    public void invalidateSource(String source) {
        String normalizedSource = source == null ? "ALL" : source.trim().toUpperCase();
        invalidateMapEntries(lastSuccessfulFetch, normalizedSource);
        invalidateMapEntries(lastSuccessfulPersist, normalizedSource);
        invalidateMapEntries(lastPublishTimestamp, normalizedSource);
        invalidateMapEntries(lastPersistHash, normalizedSource);
        invalidateMapEntries(lastPublishHash, normalizedSource);
    }

    private void invalidateMapEntries(Map<String, ?> map, String normalizedSource) {
        map.keySet().removeIf(key -> key.endsWith(":" + normalizedSource));
    }

    private boolean isFresh(Instant timestamp, Instant now, long ttlMs) {
        return timestamp != null && now.toEpochMilli() - timestamp.toEpochMilli() <= ttlMs;
    }

    private String key(String dataset, String source) {
        return (dataset == null ? "all" : dataset.trim().toLowerCase()) + ":" +
                (source == null ? "all" : source.trim().toUpperCase());
    }
}
