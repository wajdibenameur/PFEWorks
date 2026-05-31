package tn.iteam.adapter.zabbix;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ZabbixSyncStateService {

    private final AtomicLong lastSuccessfulMetricsClock = new AtomicLong(0L);
    private final AtomicLong lastSuccessfulProblemsClock = new AtomicLong(0L);
    private final AtomicLong lastSuccessfulHostsClock = new AtomicLong(0L);
    private final Map<String, AtomicLong> lastFullSyncAtEpochMsByScope = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> forceFullSyncByScope = new ConcurrentHashMap<>();

    @Value("${zabbix.sync.full-resync-interval-ms:3600000}")
    private long fullResyncIntervalMs;

    public long getLastSuccessfulMetricsClock() {
        return lastSuccessfulMetricsClock.get();
    }

    public long getLastSuccessfulProblemsClock() {
        return lastSuccessfulProblemsClock.get();
    }

    public long getLastSuccessfulHostsClock() {
        return lastSuccessfulHostsClock.get();
    }

    public void markMetricsClock(long clock) {
        if (clock > 0) {
            lastSuccessfulMetricsClock.accumulateAndGet(clock, Math::max);
        }
    }

    public void markProblemsClock(long clock) {
        if (clock > 0) {
            lastSuccessfulProblemsClock.accumulateAndGet(clock, Math::max);
        }
    }

    public void markHostsCollectedNow() {
        lastSuccessfulHostsClock.set(Instant.now().getEpochSecond());
    }

    public boolean shouldRunFullResync(String scope) {
        String normalizedScope = normalizeScope(scope);
        AtomicBoolean forceFlag = forceFullSyncByScope.computeIfAbsent(normalizedScope, key -> new AtomicBoolean(false));
        AtomicLong lastFullSync = lastFullSyncAtEpochMsByScope.computeIfAbsent(normalizedScope, key -> new AtomicLong(0L));

        if (forceFlag.getAndSet(false)) {
            return true;
        }
        long interval = Math.max(fullResyncIntervalMs, 60_000L);
        long now = System.currentTimeMillis();
        long last = lastFullSync.get();
        return last == 0L || now - last >= interval;
    }

    public void markFullSyncDoneNow(String scope) {
        String normalizedScope = normalizeScope(scope);
        lastFullSyncAtEpochMsByScope
                .computeIfAbsent(normalizedScope, key -> new AtomicLong(0L))
                .set(System.currentTimeMillis());
    }

    public void requestFullSync(String scope) {
        String normalizedScope = normalizeScope(scope);
        forceFullSyncByScope
                .computeIfAbsent(normalizedScope, key -> new AtomicBoolean(false))
                .set(true);
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "global";
        }
        return scope.trim().toLowerCase();
    }
}
