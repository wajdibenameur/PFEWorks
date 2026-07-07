package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.domain.ZabbixProblem;
import tn.iteam.dto.DashboardAnomalyDTO;
import tn.iteam.dto.DashboardOverviewDTO;
import tn.iteam.dto.DashboardPredictionDTO;
import tn.iteam.ml.dto.MlHostContext;
import tn.iteam.ml.dto.MlPredictionResult;
import tn.iteam.ml.service.MlPredictionFacadeService;
import tn.iteam.repository.MonitoredHostRepository;
import tn.iteam.repository.ZabbixMetricRepository;
import tn.iteam.repository.ZabbixProblemRepository;
import tn.iteam.service.DashboardService;
import tn.iteam.service.ZabbixDataQualityService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(
        name = "app.db.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardServiceImpl.class);
    private static final long ONE_HOUR_SECONDS = 3600L;
    private static final long TWENTY_FOUR_HOURS_SECONDS = 86400L;
    private static final double ANOMALY_ZSCORE_THRESHOLD = 3.0;

    private final ZabbixProblemRepository problemRepository;
    private final ZabbixMetricRepository metricRepository;
    private final MonitoredHostRepository monitoredHostRepository;
    private final MlPredictionFacadeService mlPredictionFacadeService;
    private final ZabbixDataQualityService dataQualityService;

    @Override
    public DashboardOverviewDTO getOverview() {
        List<DashboardPredictionDTO> predictions = getPredictions();
        List<DashboardAnomalyDTO> anomalies = getAnomalies();
        Map<String, Long> severityDistribution = problemRepository.findByActiveTrue().stream()
                .collect(Collectors.groupingBy(problem -> problem.getSeverity() == null ? "UNKNOWN" : problem.getSeverity(), LinkedHashMap::new, Collectors.counting()));

        return DashboardOverviewDTO.builder()
                .activeProblems(problemRepository.countByActiveTrue())
                .problemsBySeverity(severityDistribution)
                .predictions(predictions)
                .anomalies(anomalies)
                .dataQuality(dataQualityService.buildDatabaseQualitySummary())
                .warning("Model accuracy depends on future data quality")
                .build();
    }

    @Override
    public List<DashboardPredictionDTO> getPredictions() {
        return mlPredictionFacadeService.predictHosts(loadHostContexts()).stream()
                .map(this::toDashboardPrediction)
                .toList();
    }

    @Override
    public List<DashboardAnomalyDTO> getAnomalies() {
        List<MlHostContext> hosts = loadHostContexts();
        List<DashboardAnomalyDTO> anomalies = new ArrayList<>();

        for (MlHostContext host : hosts) {
            metricRepository.findByHostIdOrderByTimestampDesc(String.valueOf(host.hostId())).stream()
                    .collect(Collectors.groupingBy(ZabbixMetric::getMetricKey, LinkedHashMap::new, Collectors.toList()))
                    .forEach((metricKey, metrics) -> computeAnomaly(host, metricKey, metrics).ifPresent(anomalies::add));
        }

        return anomalies.stream()
                .sorted(Comparator.comparing(DashboardAnomalyDTO::anomalyScore).reversed())
                .toList();
    }

    private Optional<DashboardAnomalyDTO> computeAnomaly(MlHostContext host, String metricKey, List<ZabbixMetric> metrics) {
        if (metrics.isEmpty()) {
            return Optional.empty();
        }

        ZabbixMetric latest = metrics.stream()
                .filter(metric -> metric.getTimestamp() != null)
                .max(Comparator.comparing(ZabbixMetric::getTimestamp))
                .orElse(null);
        if (latest == null || latest.getTimestamp() == null || latest.getValue() == null) {
            return Optional.empty();
        }

        long end = latest.getTimestamp();
        long baselineStart = end - TWENTY_FOUR_HOURS_SECONDS;
        long baselineEnd = end - ONE_HOUR_SECONDS;
        List<Double> baseline = metrics.stream()
                .filter(metric -> metric.getTimestamp() != null
                        && metric.getValue() != null
                        && metric.getTimestamp() >= baselineStart
                        && metric.getTimestamp() < baselineEnd)
                .map(ZabbixMetric::getValue)
                .toList();

        if (baseline.size() < 3) {
            return Optional.empty();
        }

        double mean = baseline.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = baseline.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0.0);
        double std = Math.sqrt(variance);
        if (std < 1e-8) {
            return Optional.empty();
        }

        double zScore = Math.abs((latest.getValue() - mean) / std);
        if (zScore < ANOMALY_ZSCORE_THRESHOLD) {
            return Optional.empty();
        }

        return Optional.of(DashboardAnomalyDTO.builder()
                .hostid(host.hostId())
                .hostName(host.hostName())
                .metricName(metricKey)
                .anomalyScore(Math.min(1.0, zScore / 10.0))
                .status("ANOMALY")
                .build());
    }

    private List<MlHostContext> loadHostContexts() {
        List<MlHostContext> hosts = monitoredHostRepository.findBySourceOrderByNameAsc("ZABBIX").stream()
                .map(host -> new MlHostContext(parseHostId(host.getHostId()), host.getName()))
                .filter(host -> host.hostId() != null)
                .distinct()
                .toList();

        if (!hosts.isEmpty()) {
            return hosts;
        }

        return metricRepository.findDistinctHostIds().stream()
                .map(hostId -> new MlHostContext(parseHostId(hostId), "HOST-" + hostId))
                .filter(host -> host.hostId() != null)
                .toList();
    }

    private Long parseHostId(String hostId) {
        if (hostId == null || hostId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(hostId);
        } catch (NumberFormatException exception) {
            log.warn("Unable to parse hostId={} for dashboard", hostId);
            return null;
        }
    }

    private DashboardPredictionDTO toDashboardPrediction(MlPredictionResult result) {
        return DashboardPredictionDTO.builder()
                .hostid(result.hostId())
                .hostName(result.hostName())
                .prediction(result.prediction())
                .probability(result.probability())
                .status(result.status())
                .build();
    }
}
