package tn.iteam.monitoring.service;

import org.springframework.stereotype.Service;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringResponse;

import java.util.List;
import java.util.Map;

@Service
public class MonitoringAggregationService {

    private static final Map<String, String> METRICS_COVERAGE = Map.of(
            MonitoringSourceType.ZABBIX.name(), "supported",
            MonitoringSourceType.OBSERVIUM.name(), "not_supported",
            MonitoringSourceType.ZKBIO.name(), "not_supported"
    );

    private final MonitoringCacheService monitoringCacheService;

    public MonitoringAggregationService(MonitoringCacheService monitoringCacheService) {
        this.monitoringCacheService = monitoringCacheService;
    }

    public UnifiedMonitoringResponse<List<UnifiedMonitoringProblemDTO>> getProblems(String source) {
        MonitoringCacheService.FetchResult<List<UnifiedMonitoringProblemDTO>> result = monitoringCacheService.getProblems(source);
        return new UnifiedMonitoringResponse<>(result.getData(), result.isDegraded(), result.getFreshness(), Map.of());
    }

    public UnifiedMonitoringResponse<List<UnifiedMonitoringMetricDTO>> getMetrics(String source) {
        MonitoringCacheService.FetchResult<List<UnifiedMonitoringMetricDTO>> result = monitoringCacheService.getMetrics(source);
        return new UnifiedMonitoringResponse<>(result.getData(), result.isDegraded(), result.getFreshness(), metricsCoverage(source));
    }

    public UnifiedMonitoringResponse<List<UnifiedMonitoringHostDTO>> getHosts(String source) {
        MonitoringCacheService.FetchResult<List<UnifiedMonitoringHostDTO>> result = monitoringCacheService.getHosts(source);
        return new UnifiedMonitoringResponse<>(result.getData(), result.isDegraded(), result.getFreshness(), Map.of());
    }

    public UnifiedMonitoringResponse<List<UnifiedMonitoringProblemDTO>> getProblems(MonitoringSourceType source) {
        return getProblems(source != null ? source.name() : null);
    }

    public UnifiedMonitoringResponse<List<UnifiedMonitoringMetricDTO>> getMetrics(MonitoringSourceType source) {
        return getMetrics(source != null ? source.name() : null);
    }

    public UnifiedMonitoringResponse<List<UnifiedMonitoringHostDTO>> getHosts(MonitoringSourceType source) {
        return getHosts(source != null ? source.name() : null);
    }

    private Map<String, String> metricsCoverage(String source) {
        if (source == null || source.isBlank() || "ALL".equalsIgnoreCase(source)) {
            return METRICS_COVERAGE;
        }

        MonitoringSourceType requested = MonitoringSourceType.valueOf(source.trim().toUpperCase());
        return Map.of(requested.name(), METRICS_COVERAGE.getOrDefault(requested.name(), "not_supported"));
    }
}
