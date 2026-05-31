package tn.iteam.service.impl;

import ai.djl.ModelException;
import ai.djl.translate.TranslateException;
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
import tn.iteam.ml.dto.TorchScriptPredictionResponse;
import tn.iteam.ml.service.TorchScriptPredictionService;
import tn.iteam.repository.MonitoredHostRepository;
import tn.iteam.repository.ZabbixMetricRepository;
import tn.iteam.repository.ZabbixProblemRepository;
import tn.iteam.service.DashboardService;
import tn.iteam.service.ZabbixDataQualityService;

import java.io.IOException;
import java.time.Instant;
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
    private final TorchScriptPredictionService predictionService;
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
        if (!predictionService.isEnabled()) {
            log.info("Dashboard predictions disabled: {}", predictionService.getDisabledReason());
            return List.of();
        }

        List<HostContext> hosts = loadHostContexts();
        List<DashboardPredictionDTO> predictions = new ArrayList<>();

        for (HostContext host : hosts) {
            try {
                List<Double> features = buildPredictionFeatures(host.hostId());
                TorchScriptPredictionResponse response = predictionService.predict(features);
                predictions.add(DashboardPredictionDTO.builder()
                        .hostid(host.hostId())
                        .hostName(host.hostName())
                        .prediction(response.prediction())
                        .probability(response.probability())
                        .status(toRiskStatus(response.probability()))
                        .build());
            } catch (IOException | ModelException | TranslateException | IllegalArgumentException | IllegalStateException exception) {
                log.debug("Unable to compute prediction for host {}: {}", host.hostId(), exception.getMessage());
            }
        }

        return predictions.stream()
                .sorted(Comparator.comparing(DashboardPredictionDTO::probability).reversed())
                .toList();
    }

    @Override
    public List<DashboardAnomalyDTO> getAnomalies() {
        List<HostContext> hosts = loadHostContexts();
        List<DashboardAnomalyDTO> anomalies = new ArrayList<>();

        for (HostContext host : hosts) {
            metricRepository.findByHostIdOrderByTimestampDesc(String.valueOf(host.hostId())).stream()
                    .collect(Collectors.groupingBy(ZabbixMetric::getMetricKey, LinkedHashMap::new, Collectors.toList()))
                    .forEach((metricKey, metrics) -> computeAnomaly(host, metricKey, metrics).ifPresent(anomalies::add));
        }

        return anomalies.stream()
                .sorted(Comparator.comparing(DashboardAnomalyDTO::anomalyScore).reversed())
                .toList();
    }

    private Optional<DashboardAnomalyDTO> computeAnomaly(HostContext host, String metricKey, List<ZabbixMetric> metrics) {
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

    private List<Double> buildPredictionFeatures(Long hostId) {
        String hostIdText = String.valueOf(hostId);
        List<ZabbixMetric> metrics = metricRepository.findByHostIdOrderByTimestampDesc(hostIdText);
        List<ZabbixProblem> problems = problemRepository.findByHostIdOrderByStartedAtDesc(hostId);

        long referenceTime = metrics.stream()
                .map(ZabbixMetric::getTimestamp)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElseGet(() -> problems.stream()
                        .map(ZabbixProblem::getStartedAt)
                        .filter(Objects::nonNull)
                        .max(Long::compareTo)
                        .orElse(Instant.now().getEpochSecond()));

        long oneHourStart = referenceTime - ONE_HOUR_SECONDS;
        long twentyFourHourStart = referenceTime - TWENTY_FOUR_HOURS_SECONDS;

        List<Double> metricsLast1h = metrics.stream()
                .filter(metric -> metric.getTimestamp() != null && metric.getTimestamp() >= oneHourStart && metric.getTimestamp() < referenceTime)
                .map(ZabbixMetric::getValue)
                .filter(Objects::nonNull)
                .toList();
        List<Double> metricsPrev23h = metrics.stream()
                .filter(metric -> metric.getTimestamp() != null && metric.getTimestamp() >= twentyFourHourStart && metric.getTimestamp() < oneHourStart)
                .map(ZabbixMetric::getValue)
                .filter(Objects::nonNull)
                .toList();

        List<Double> cpuLast1h = metricValuesByPrefix(metrics, oneHourStart, referenceTime, "system.cpu.util", "system.cpu.load");
        List<Double> cpuPrev23h = metricValuesByPrefix(metrics, twentyFourHourStart, oneHourStart, "system.cpu.util", "system.cpu.load");
        List<Double> ramUsedLast1h = metricValuesByPrefix(metrics, oneHourStart, referenceTime, "vm.memory.size[used]");
        List<Double> ramAvailableLast1h = metricValuesByPrefix(metrics, oneHourStart, referenceTime, "vm.memory.size[available]", "vm.memory.size[pavailable]");
        List<Double> diskPusedLast1h = metricValuesByPrefix(metrics, oneHourStart, referenceTime, "vfs.fs.size");
        List<Double> pingLossLast1h = metricValuesByPrefix(metrics, oneHourStart, referenceTime, "icmppingloss");
        List<Double> pingRttLast1h = metricValuesByPrefix(metrics, oneHourStart, referenceTime, "icmppingsec");
        List<Double> netInLast1h = metricValuesByPrefix(metrics, oneHourStart, referenceTime, "net.if.in");
        List<Double> netOutLast1h = metricValuesByPrefix(metrics, oneHourStart, referenceTime, "net.if.out");
        List<Double> uptimeLast1h = metricValuesByPrefix(metrics, oneHourStart, referenceTime, "system.uptime");

        long problemCountLast1h = problems.stream()
                .filter(problem -> problem.getStartedAt() != null && problem.getStartedAt() >= oneHourStart && problem.getStartedAt() < referenceTime)
                .count();
        long problemCountLast24h = problems.stream()
                .filter(problem -> problem.getStartedAt() != null && problem.getStartedAt() >= twentyFourHourStart && problem.getStartedAt() < referenceTime)
                .count();
        long timeSinceLastProblem = problems.stream()
                .map(ZabbixProblem::getStartedAt)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .map(lastProblem -> Math.max(0L, referenceTime - lastProblem))
                .orElse(TWENTY_FOUR_HOURS_SECONDS * 7);

        double avgMetricLast1h = metricsLast1h.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double maxMetricLast1h = metricsLast1h.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double avgMetricPrev23h = metricsPrev23h.stream().mapToDouble(Double::doubleValue).average().orElse(avgMetricLast1h);
        double trendMetric = avgMetricLast1h - avgMetricPrev23h;
        double cpuAvgLast1h = cpuLast1h.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double cpuMaxLast1h = cpuLast1h.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double cpuAvgPrev23h = cpuPrev23h.stream().mapToDouble(Double::doubleValue).average().orElse(cpuAvgLast1h);
        double cpuTrend = cpuAvgLast1h - cpuAvgPrev23h;
        double ramUsedAvgLast1h = ramUsedLast1h.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double ramAvailableAvgLast1h = ramAvailableLast1h.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double diskPusedAvgLast1h = diskPusedLast1h.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double pingLossAvgLast1h = pingLossLast1h.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double pingRttAvgLast1h = pingRttLast1h.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double netInAvgLast1h = netInLast1h.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double netOutAvgLast1h = netOutLast1h.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double uptimeLatest = uptimeLast1h.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        Map<String, Double> featureMap = new LinkedHashMap<>();
        featureMap.put("problem_count_last_1h", (double) problemCountLast1h);
        featureMap.put("problem_count_last_24h", (double) problemCountLast24h);
        featureMap.put("avg_metric_last_1h", avgMetricLast1h);
        featureMap.put("max_metric_last_1h", maxMetricLast1h);
        featureMap.put("trend_metric", trendMetric);
        featureMap.put("time_since_last_problem", (double) timeSinceLastProblem);
        // 10 operationally relevant features for risk prediction.
        featureMap.put("cpu_avg_last_1h", cpuAvgLast1h);
        featureMap.put("cpu_max_last_1h", cpuMaxLast1h);
        featureMap.put("cpu_trend", cpuTrend);
        featureMap.put("ram_used_avg_last_1h", ramUsedAvgLast1h);
        featureMap.put("ram_available_avg_last_1h", ramAvailableAvgLast1h);
        featureMap.put("disk_pused_avg_last_1h", diskPusedAvgLast1h);
        featureMap.put("ping_loss_avg_last_1h", pingLossAvgLast1h);
        featureMap.put("ping_rtt_avg_last_1h", pingRttAvgLast1h);
        featureMap.put("net_in_avg_last_1h", netInAvgLast1h);
        featureMap.put("net_out_avg_last_1h", netOutAvgLast1h);
        featureMap.put("uptime_latest", uptimeLatest);

        return predictionService.getFeatureOrder().stream()
                .map(featureName -> featureMap.getOrDefault(featureName, 0.0))
                .toList();
    }

    private List<Double> metricValuesByPrefix(
            List<ZabbixMetric> metrics,
            long startInclusive,
            long endExclusive,
            String... prefixes
    ) {
        return metrics.stream()
                .filter(metric -> metric.getTimestamp() != null
                        && metric.getTimestamp() >= startInclusive
                        && metric.getTimestamp() < endExclusive)
                .filter(metric -> metric.getMetricKey() != null)
                .filter(metric -> matchesAnyPrefix(metric.getMetricKey(), prefixes))
                .map(ZabbixMetric::getValue)
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean matchesAnyPrefix(String metricKey, String... prefixes) {
        String normalizedKey = metricKey.toLowerCase();
        for (String prefix : prefixes) {
            if (normalizedKey.startsWith(prefix.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private List<HostContext> loadHostContexts() {
        List<HostContext> hosts = monitoredHostRepository.findBySourceOrderByNameAsc("ZABBIX").stream()
                .map(host -> new HostContext(parseHostId(host.getHostId()), host.getName()))
                .filter(host -> host.hostId() != null)
                .distinct()
                .toList();

        if (!hosts.isEmpty()) {
            return hosts;
        }

        return metricRepository.findDistinctHostIds().stream()
                .map(hostId -> new HostContext(parseHostId(hostId), "HOST-" + hostId))
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

    private String toRiskStatus(double probability) {
        if (probability >= 0.80) {
            return "RISK";
        }
        if (probability >= 0.55) {
            return "WATCH";
        }
        return "NORMAL";
    }

    private record HostContext(Long hostId, String hostName) {
    }
}
