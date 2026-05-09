package tn.iteam.monitoring.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.snapshot.SnapshotStore;
import tn.iteam.monitoring.snapshot.StoredSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MonitoringCacheService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringCacheService.class);
    private static final String DATASET_PROBLEMS = "problems";
    private static final String DATASET_METRICS = "metrics";
    private static final String DATASET_HOSTS = "hosts";

    private final SnapshotStore snapshotStore;

    public MonitoringCacheService(SnapshotStore snapshotStore) {
        this.snapshotStore = snapshotStore;
    }

    public FetchResult<List<UnifiedMonitoringProblemDTO>> getProblems(String source) {
        return loadOrRefresh(
                DATASET_PROBLEMS,
                source,
                items -> items.stream()
                        .sorted(Comparator.comparing(UnifiedMonitoringProblemDTO::getStartedAt, Comparator.nullsLast(Long::compareTo)).reversed())
                        .toList()
        );
    }

    public FetchResult<List<UnifiedMonitoringMetricDTO>> getMetrics(String source) {
        return loadOrRefresh(
                DATASET_METRICS,
                source,
                items -> items.stream()
                        .sorted(Comparator.comparing(UnifiedMonitoringMetricDTO::getTimestamp, Comparator.nullsLast(Long::compareTo)).reversed())
                        .toList()
        );
    }

    public FetchResult<List<UnifiedMonitoringHostDTO>> getHosts(String source) {
        return loadOrRefresh(
                DATASET_HOSTS,
                source,
                items -> items.stream()
                        .sorted(Comparator.comparing(UnifiedMonitoringHostDTO::getSource)
                                .thenComparing(UnifiedMonitoringHostDTO::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                        .toList()
        );
    }

    private <T> FetchResult<List<T>> loadOrRefresh(
            String dataset,
            String source,
            ResultSorter<T> sorter
    ) {
        String normalizedSource = normalizeSource(source);
        List<MonitoringSourceType> sources = requestedSources(dataset, normalizedSource);
        List<T> aggregated = new ArrayList<>();
        boolean degraded = false;
        Map<String, String> freshness = new LinkedHashMap<>();

        for (MonitoringSourceType sourceType : sources) {
            snapshotStore.<List<T>>get(dataset, sourceType.name())
                    .ifPresentOrElse(snapshot -> {
                        if (snapshot.data() != null) {
                            aggregated.addAll(snapshot.data());
                        }
                        freshness.put(sourceType.name(), firstFreshness(snapshot.freshness()));
                        if (snapshot.degraded()) {
                            log.debug("Serving degraded monitoring {} snapshot for source={}", dataset, sourceType.name());
                        }
                    }, () -> {
                        freshness.put(sourceType.name(), StoredSnapshot.FRESHNESS_SNAPSHOT_MISSING);
                    });

            if (StoredSnapshot.FRESHNESS_SNAPSHOT_MISSING.equals(freshness.get(sourceType.name()))) {
                degraded = true;
            } else {
                degraded = degraded || snapshotStore.<List<T>>get(dataset, sourceType.name())
                        .map(snapshot -> snapshot.degraded())
                        .orElse(true);
            }
        }

        return new FetchResult<>(sorter.sort(List.copyOf(aggregated)), degraded, Map.copyOf(freshness));
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank() || "ALL".equalsIgnoreCase(source)) {
            return "ALL";
        }
        return source.trim().toUpperCase(Locale.ROOT);
    }

    private List<MonitoringSourceType> requestedSources(String dataset, String source) {
        if (source == null || source.isBlank() || "ALL".equalsIgnoreCase(source)) {
            return java.util.Arrays.stream(MonitoringSourceType.values())
                    .filter(sourceType -> sourceType.supportsDataset(dataset))
                    .toList();
        }

        MonitoringSourceType requested = MonitoringSourceType.valueOf(source.trim().toUpperCase(Locale.ROOT));
        if (!requested.supportsDataset(dataset)) {
            return List.of();
        }
        return List.of(requested);
    }

    @FunctionalInterface
    private interface ResultSorter<T> {
        List<T> sort(List<T> items);
    }

    private String firstFreshness(Map<String, String> freshness) {
        if (freshness == null || freshness.isEmpty()) {
            return StoredSnapshot.FRESHNESS_SNAPSHOT_FALLBACK;
        }
        return freshness.values().iterator().next();
    }

    public static final class FetchResult<T> {
        private final T data;
        private final boolean degraded;
        private final Map<String, String> freshness;

        public FetchResult(T data, boolean degraded, Map<String, String> freshness) {
            this.data = data;
            this.degraded = degraded;
            this.freshness = freshness;
        }

        public T getData() {
            return data;
        }

        public boolean isDegraded() {
            return degraded;
        }

        public Map<String, String> getFreshness() {
            return freshness;
        }
    }
}
