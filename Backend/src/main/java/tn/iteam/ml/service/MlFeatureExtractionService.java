package tn.iteam.ml.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.domain.ZabbixProblem;
import tn.iteam.ml.config.MlRiskProperties;
import tn.iteam.ml.dto.MlHostContext;
import tn.iteam.ml.dto.MlHostFeatureSet;
import tn.iteam.repository.ZabbixMetricRepository;
import tn.iteam.repository.ZabbixProblemRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MlFeatureExtractionService {

    private static final long ONE_HOUR_SECONDS = 3600L;
    private static final long TWENTY_FOUR_HOURS_SECONDS = 86400L;
    private static final long SEVEN_DAYS_SECONDS = 7 * TWENTY_FOUR_HOURS_SECONDS;
    private static final List<String> DEFAULT_FEATURE_ORDER = List.of(
            "cpu_usage_percent",
            "ram_usage_percent",
            "latency_ms",
            "traffic_in_bps",
            "traffic_out_bps",
            "interface_errors_in",
            "interface_errors_out",
            "packet_loss_percent",
            "availability_status",
            "temperature_celsius",
            "problem_count_last_1h",
            "problem_count_last_24h",
            "critical_problem_count_last_1h",
            "critical_problem_count_last_24h",
            "high_problem_count_last_1h",
            "disaster_problem_count_last_1h",
            "last_problem_severity",
            "active_critical_problems",
            "time_since_last_critical_problem_minutes"
    );

    private final ZabbixMetricRepository metricRepository;
    private final ZabbixProblemRepository problemRepository;
    private final TorchScriptPredictionService predictionService;
    private final MlRiskProperties riskProperties;

    public List<MlHostFeatureSet> buildFeatureSets(List<MlHostContext> hosts) {
        List<MlHostContext> validHosts = hosts.stream()
                .filter(Objects::nonNull)
                .filter(host -> host.hostId() != null)
                .toList();
        if (validHosts.isEmpty()) {
            return List.of();
        }

        long since = Instant.now().getEpochSecond() - SEVEN_DAYS_SECONDS;
        List<String> hostIds = validHosts.stream().map(host -> String.valueOf(host.hostId())).toList();
        List<Long> longHostIds = validHosts.stream().map(MlHostContext::hostId).toList();

        Map<Long, List<ZabbixMetric>> metricsByHost = metricRepository.findRecentMetricsForHosts(hostIds, since).stream()
                .filter(metric -> metric.getHostId() != null)
                .collect(Collectors.groupingBy(metric -> parseHostId(metric.getHostId()), LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<ZabbixProblem>> problemsByHost = problemRepository.findRecentProblemsForHosts(longHostIds, since).stream()
                .filter(problem -> problem.getHostId() != null)
                .collect(Collectors.groupingBy(ZabbixProblem::getHostId, LinkedHashMap::new, Collectors.toList()));

        List<String> featureOrder = predictionService.getFeatureOrder().isEmpty()
                ? DEFAULT_FEATURE_ORDER
                : predictionService.getFeatureOrder();

        List<MlHostFeatureSet> featureSets = new ArrayList<>();
        for (MlHostContext host : validHosts) {
            List<ZabbixMetric> hostMetrics = metricsByHost.getOrDefault(host.hostId(), List.of());
            List<ZabbixProblem> hostProblems = problemsByHost.getOrDefault(host.hostId(), List.of());
            featureSets.add(buildFeatureSet(host, hostMetrics, hostProblems, featureOrder));
        }
        return featureSets;
    }

    private MlHostFeatureSet buildFeatureSet(
            MlHostContext host,
            List<ZabbixMetric> metrics,
            List<ZabbixProblem> problems,
            List<String> featureOrder
    ) {
        long referenceTime = resolveReferenceTime(metrics, problems);
        boolean hasEnoughData = !metrics.isEmpty() || !problems.isEmpty();

        Map<String, Double> featureValues = new LinkedHashMap<>();
        featureValues.put("cpu_usage_percent", sanitizeCpu(resolveCpuUsagePercent(metrics)));
        featureValues.put("ram_usage_percent", sanitizePercent(resolveRamUsagePercent(metrics)));
        featureValues.put("latency_ms", sanitizeNonNegative(resolveLatencyMs(metrics)));
        featureValues.put("traffic_in_bps", sanitizeNonNegative(resolveLatestMetric(metrics, Set.of("net.if.in"))));
        featureValues.put("traffic_out_bps", sanitizeNonNegative(resolveLatestMetric(metrics, Set.of("net.if.out"))));
        featureValues.put("interface_errors_in", sanitizeNonNegative(resolveLatestMetric(metrics, Set.of("net.if.in.errors"))));
        featureValues.put("interface_errors_out", sanitizeNonNegative(resolveLatestMetric(metrics, Set.of("net.if.out.errors"))));
        featureValues.put("packet_loss_percent", sanitizePercent(resolveLatestMetric(metrics, Set.of("icmppingloss", "packet.loss"))));
        featureValues.put("availability_status", sanitizeAvailability(resolveAvailability(metrics)));
        featureValues.put("temperature_celsius", sanitizeTemperature(resolveLatestMetric(metrics, Set.of("sensor.temp", "temperature", "system.hw.temperature"))));
        featureValues.put("problem_count_last_1h", (double) countProblemsWithin(problems, referenceTime, ONE_HOUR_SECONDS));
        featureValues.put("problem_count_last_24h", (double) countProblemsWithin(problems, referenceTime, TWENTY_FOUR_HOURS_SECONDS));
        featureValues.put("critical_problem_count_last_1h", (double) countProblemsWithin(problems, referenceTime, ONE_HOUR_SECONDS, riskProperties.criticalSeverityThreshold()));
        featureValues.put("critical_problem_count_last_24h", (double) countProblemsWithin(problems, referenceTime, TWENTY_FOUR_HOURS_SECONDS, riskProperties.criticalSeverityThreshold()));
        featureValues.put("high_problem_count_last_1h", (double) countProblemsWithin(problems, referenceTime, ONE_HOUR_SECONDS, riskProperties.highSeverityThreshold()));
        featureValues.put("disaster_problem_count_last_1h", (double) countProblemsWithin(problems, referenceTime, ONE_HOUR_SECONDS, riskProperties.disasterSeverityThreshold()));
        featureValues.put("last_problem_severity", sanitizeNonNegative(resolveLastProblemSeverity(problems)));
        featureValues.put("active_critical_problems", (double) countActiveProblems(problems, riskProperties.criticalSeverityThreshold()));
        featureValues.put("time_since_last_critical_problem_minutes", sanitizeNonNegative(resolveMinutesSinceLastCriticalProblem(problems, referenceTime)));

        List<Double> orderedFeatures = featureOrder.stream()
                .map(featureName -> featureValues.getOrDefault(featureName, 0.0d))
                .toList();

        String status = hasEnoughData ? "READY" : "UNKNOWN";
        return new MlHostFeatureSet(host.hostId(), host.hostName(), featureValues, orderedFeatures, hasEnoughData, status);
    }

    private long resolveReferenceTime(List<ZabbixMetric> metrics, List<ZabbixProblem> problems) {
        long metricMax = metrics.stream()
                .map(ZabbixMetric::getTimestamp)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L);
        long problemMax = problems.stream()
                .map(ZabbixProblem::getStartedAt)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L);
        long reference = Math.max(metricMax, problemMax);
        return reference > 0 ? reference : Instant.now().getEpochSecond();
    }

    private double resolveCpuUsagePercent(List<ZabbixMetric> metrics) {
        return firstNonZero(
                resolveLatestMetric(metrics, Set.of("system.cpu.util", "cpu.util")),
                resolveLatestMetric(metrics, Set.of("system.cpu.load"))
        );
    }

    private double resolveRamUsagePercent(List<ZabbixMetric> metrics) {
        double directUsage = resolveLatestMetric(metrics, Set.of("vm.memory.util", "memory.util", "vm.memory.size[pused]"));
        if (directUsage > 0) {
            return directUsage;
        }

        double used = resolveLatestMetric(metrics, Set.of("vm.memory.size[used]"));
        double total = resolveLatestMetric(metrics, Set.of("vm.memory.size[total]"));
        if (used > 0 && total > 0) {
            return (used / total) * 100.0;
        }

        double available = resolveLatestMetric(metrics, Set.of("vm.memory.size[available]"));
        if (used > 0 && available > 0) {
            return (used / (used + available)) * 100.0;
        }

        double percentAvailable = resolveLatestMetric(metrics, Set.of("vm.memory.size[pavailable]"));
        if (percentAvailable > 0) {
            return 100.0 - percentAvailable;
        }
        return 0.0;
    }

    private double resolveLatencyMs(List<ZabbixMetric> metrics) {
        Optional<ZabbixMetric> latencyMetric = latestMetric(metrics, Set.of("icmppingsec", "net.tcp.service.perf"));
        if (latencyMetric.isPresent()) {
            return latencyMetric.get().getValue() == null ? 0.0 : latencyMetric.get().getValue() * 1000.0;
        }
        return 0.0;
    }

    private double resolveAvailability(List<ZabbixMetric> metrics) {
        double availability = resolveLatestMetric(metrics, Set.of("icmpping", "agent.ping", "zabbix[host,available]"));
        return availability > 0 ? 1.0 : 0.0;
    }

    private double resolveLatestMetric(List<ZabbixMetric> metrics, Set<String> prefixes) {
        return latestMetric(metrics, prefixes)
                .map(ZabbixMetric::getValue)
                .filter(Objects::nonNull)
                .orElse(0.0);
    }

    private Optional<ZabbixMetric> latestMetric(List<ZabbixMetric> metrics, Set<String> prefixes) {
        return metrics.stream()
                .filter(metric -> metric.getMetricKey() != null)
                .filter(metric -> matchesAnyPrefix(metric.getMetricKey(), prefixes))
                .filter(metric -> metric.getTimestamp() != null)
                .max(Comparator.comparing(ZabbixMetric::getTimestamp));
    }

    private boolean matchesAnyPrefix(String metricKey, Set<String> prefixes) {
        String normalizedKey = metricKey.toLowerCase();
        for (String prefix : prefixes) {
            if (normalizedKey.startsWith(prefix.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private long countProblemsWithin(List<ZabbixProblem> problems, long referenceTime, long windowSeconds) {
        long start = referenceTime - windowSeconds;
        return problems.stream()
                .filter(problem -> problem.getStartedAt() != null)
                .filter(problem -> problem.getStartedAt() >= start && problem.getStartedAt() < referenceTime)
                .count();
    }

    private long countProblemsWithin(List<ZabbixProblem> problems, long referenceTime, long windowSeconds, int severityThreshold) {
        long start = referenceTime - windowSeconds;
        return problems.stream()
                .filter(problem -> problem.getStartedAt() != null)
                .filter(problem -> problem.getStartedAt() >= start && problem.getStartedAt() < referenceTime)
                .filter(problem -> severity(problem) >= severityThreshold)
                .count();
    }

    private long countActiveProblems(List<ZabbixProblem> problems, int severityThreshold) {
        return problems.stream()
                .filter(problem -> Boolean.TRUE.equals(problem.getActive()))
                .filter(problem -> severity(problem) >= severityThreshold)
                .count();
    }

    private double resolveLastProblemSeverity(List<ZabbixProblem> problems) {
        return problems.stream()
                .filter(problem -> problem.getStartedAt() != null)
                .max(Comparator.comparing(ZabbixProblem::getStartedAt))
                .map(this::severity)
                .orElse(0);
    }

    private double resolveMinutesSinceLastCriticalProblem(List<ZabbixProblem> problems, long referenceTime) {
        return problems.stream()
                .filter(problem -> severity(problem) >= riskProperties.criticalSeverityThreshold())
                .map(ZabbixProblem::getStartedAt)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .map(problemTime -> Math.max(0L, referenceTime - problemTime) / 60.0)
                .orElse(SEVEN_DAYS_SECONDS / 60.0);
    }

    private int severity(ZabbixProblem problem) {
        if (problem == null || problem.getSeverity() == null || problem.getSeverity().isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(problem.getSeverity().trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private Long parseHostId(String hostId) {
        if (hostId == null || hostId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(hostId);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private double firstNonZero(double... values) {
        for (double value : values) {
            if (value > 0.0) {
                return value;
            }
        }
        return 0.0;
    }

    private double sanitizeCpu(double value) {
        return clampFinite(value, 0.0, 100.0);
    }

    private double sanitizePercent(double value) {
        return clampFinite(value, 0.0, 100.0);
    }

    private double sanitizeAvailability(double value) {
        return value > 0.0 ? 1.0 : 0.0;
    }

    private double sanitizeTemperature(double value) {
        return clampFinite(value, -50.0, 150.0);
    }

    private double sanitizeNonNegative(double value) {
        return clampFinite(value, 0.0, Double.MAX_VALUE);
    }

    private double clampFinite(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(min, Math.min(max, value));
    }
}
