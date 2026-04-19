package tn.iteam.monitoring.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.provider.MonitoringProvider;
import tn.iteam.service.SourceAvailabilityService;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MonitoringCacheService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringCacheService.class);
    private static final String FRESHNESS_LIVE = "live";
    private static final String FRESHNESS_PERSISTED = "persisted";
    private static final String FRESHNESS_REDIS_FALLBACK = "redis_fallback";

    private final List<MonitoringProvider> providers;
    private final SourceAvailabilityService sourceAvailabilityService;

    public MonitoringCacheService(List<MonitoringProvider> providers, SourceAvailabilityService sourceAvailabilityService) {
        this.providers = List.copyOf(providers);
        this.sourceAvailabilityService = sourceAvailabilityService;
    }

    @Cacheable(
            cacheNames = "monitoring:problems",
            key = "#source == null ? 'ALL' : #source.trim().toUpperCase()",
            unless = "#result == null || #result.degraded"
    )
    public FetchResult<List<UnifiedMonitoringProblemDTO>> getProblems(String source) {
        FetchResult<List<UnifiedMonitoringProblemDTO>> result = collect(source, "problems", provider -> provider.getProblems());
        List<UnifiedMonitoringProblemDTO> sorted = result.getData().stream()
                .sorted(Comparator.comparing(UnifiedMonitoringProblemDTO::getStartedAt, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
        return new FetchResult<>(sorted, result.isDegraded(), result.getFreshness());
    }

    @Cacheable(
            cacheNames = "monitoring:metrics",
            key = "#source == null ? 'ALL' : #source.trim().toUpperCase()",
            unless = "#result == null || #result.degraded"
    )
    public FetchResult<List<UnifiedMonitoringMetricDTO>> getMetrics(String source) {
        FetchResult<List<UnifiedMonitoringMetricDTO>> result = collect(source, "metrics", provider -> provider.getMetrics());
        List<UnifiedMonitoringMetricDTO> sorted = result.getData().stream()
                .sorted(Comparator.comparing(UnifiedMonitoringMetricDTO::getTimestamp, Comparator.nullsLast(Long::compareTo)).reversed())
                .toList();
        return new FetchResult<>(sorted, result.isDegraded(), result.getFreshness());
    }

    @Cacheable(
            cacheNames = "monitoring:hosts",
            key = "#source == null ? 'ALL' : #source.trim().toUpperCase()",
            unless = "#result == null || #result.degraded"
    )
    public FetchResult<List<UnifiedMonitoringHostDTO>> getHosts(String source) {
        FetchResult<List<UnifiedMonitoringHostDTO>> result = collect(source, "hosts", provider -> provider.getHosts());
        List<UnifiedMonitoringHostDTO> sorted = result.getData().stream()
                .sorted(Comparator.comparing(UnifiedMonitoringHostDTO::getSource)
                        .thenComparing(UnifiedMonitoringHostDTO::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        return new FetchResult<>(sorted, result.isDegraded(), result.getFreshness());
    }

    private <T> FetchResult<List<T>> collect(
            String source,
            String operation,
            ProviderFetcher<T> fetcher
    ) {
        List<T> aggregated = new java.util.ArrayList<>();
        boolean degraded = false;
        Map<String, String> freshness = new LinkedHashMap<>();

        for (MonitoringProvider provider : providersFor(source)) {
            try {
                List<T> items = fetcher.fetch(provider);
                if (items != null) {
                    aggregated.addAll(items);
                }
                freshness.put(provider.getSourceType().name(), determineFreshness(provider.getSourceType()));
            } catch (Exception ex) {
                degraded = true;
                log.warn("Monitoring provider {} failed while fetching {}: {}",
                        provider.getSourceType(), operation, ex.getMessage(), ex);
            }
        }

        return new FetchResult<>(List.copyOf(aggregated), degraded, Map.copyOf(freshness));
    }

    private String determineFreshness(MonitoringSourceType sourceType) {
        if (sourceType == MonitoringSourceType.ZABBIX) {
            return FRESHNESS_PERSISTED;
        }

        return sourceAvailabilityService.isDegraded(sourceType.name())
                ? FRESHNESS_REDIS_FALLBACK
                : FRESHNESS_LIVE;
    }

    private List<MonitoringProvider> providersFor(String source) {
        if (source == null || source.isBlank() || "ALL".equalsIgnoreCase(source)) {
            return providers;
        }

        MonitoringSourceType requested = MonitoringSourceType.valueOf(source.trim().toUpperCase(Locale.ROOT));
        return providers.stream()
                .filter(provider -> provider.getSourceType() == requested)
                .toList();
    }

    @FunctionalInterface
    private interface ProviderFetcher<T> {
        List<T> fetch(MonitoringProvider provider);
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
