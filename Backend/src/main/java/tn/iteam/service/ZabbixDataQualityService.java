package tn.iteam.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.domain.ZabbixProblem;
import tn.iteam.repository.ZabbixMetricRepository;
import tn.iteam.repository.ZabbixProblemRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ZabbixDataQualityService {

    private static final Logger log = LoggerFactory.getLogger(ZabbixDataQualityService.class);

    private final ZabbixProblemRepository problemRepository;
    private final ZabbixMetricRepository metricRepository;

    public void logProblemQualitySummary(List<ZabbixProblem> latestBatch) {
        long nullStartedAt = latestBatch.stream().filter(problem -> problem.getStartedAt() == null || problem.getStartedAt() <= 0).count();
        long nullStatus = latestBatch.stream().filter(problem -> problem.getStatus() == null || problem.getStatus().isBlank()).count();
        long nullHostId = latestBatch.stream().filter(problem -> problem.getHostId() == null).count();

        Map<String, Long> severityDistribution = latestBatch.stream()
                .collect(Collectors.groupingBy(problem -> problem.getSeverity() == null ? "UNKNOWN" : problem.getSeverity(), LinkedHashMap::new, Collectors.counting()));

        log.info(
                "Zabbix problem quality | batchSize={} | nullStartedAt={} | nullStatus={} | nullHostId={} | severityDistribution={}",
                latestBatch.size(),
                nullStartedAt,
                nullStatus,
                nullHostId,
                severityDistribution
        );
    }

    public void logMetricQualitySummary(List<ZabbixMetric> latestBatch) {
        long nullTimestamp = latestBatch.stream().filter(metric -> metric.getTimestamp() == null || metric.getTimestamp() <= 0).count();
        long nullHostId = latestBatch.stream().filter(metric -> metric.getHostId() == null || metric.getHostId().isBlank()).count();
        long nullMetricKey = latestBatch.stream().filter(metric -> metric.getMetricKey() == null || metric.getMetricKey().isBlank()).count();

        log.info(
                "Zabbix metric quality | batchSize={} | nullTimestamp={} | nullHostId={} | nullMetricKey={}",
                latestBatch.size(),
                nullTimestamp,
                nullHostId,
                nullMetricKey
        );
    }

    public Map<String, Object> buildDatabaseQualitySummary() {
        List<ZabbixProblem> problems = problemRepository.findAll();
        List<ZabbixMetric> metrics = metricRepository.findAll();

        Map<String, Long> severityDistribution = problems.stream()
                .collect(Collectors.groupingBy(problem -> problem.getSeverity() == null ? "UNKNOWN" : problem.getSeverity(), LinkedHashMap::new, Collectors.counting()));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("problemCount", problems.size());
        summary.put("metricCount", metrics.size());
        summary.put("problemsWithoutStartedAt", problems.stream().filter(problem -> problem.getStartedAt() == null || problem.getStartedAt() <= 0).count());
        summary.put("problemsWithoutResolvedAt", problems.stream().filter(problem -> !Boolean.TRUE.equals(problem.getActive()) && (problem.getResolvedAt() == null || problem.getResolvedAt() <= 0)).count());
        summary.put("problemsWithoutStatus", problems.stream().filter(problem -> problem.getStatus() == null || problem.getStatus().isBlank()).count());
        summary.put("metricsWithoutTimestamp", metrics.stream().filter(metric -> metric.getTimestamp() == null || metric.getTimestamp() <= 0).count());
        summary.put("severityDistribution", severityDistribution);
        summary.put("warning", "Model accuracy depends on future data quality");
        return summary;
    }
}
