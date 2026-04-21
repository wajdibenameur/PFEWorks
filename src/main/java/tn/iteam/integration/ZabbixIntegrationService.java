package tn.iteam.integration;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.mapper.ZabbixMonitoringMapper;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.snapshot.SnapshotStore;
import tn.iteam.monitoring.snapshot.StoredSnapshot;
import tn.iteam.service.ServiceStatusPersistenceService;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.ZabbixMetricsService;
import tn.iteam.service.ZabbixProblemService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ZabbixIntegrationService implements IntegrationService {

    private static final Logger log = LoggerFactory.getLogger(ZabbixIntegrationService.class);
    private static final String DATASET_HOSTS = "hosts";
    private static final String DATASET_METRICS = "metrics";
    private static final String DATASET_PROBLEMS = "problems";
    private static final String FRESHNESS_LIVE = "live";
    private static final String FRESHNESS_SNAPSHOT = "snapshot_fallback";

    private final ZabbixAdapter zabbixAdapter;
    private final ZabbixMonitoringMapper monitoringMapper;
    private final ServiceStatusPersistenceService serviceStatusPersistenceService;
    private final ZabbixProblemService zabbixProblemService;
    private final ZabbixMetricsService zabbixMetricsService;
    private final SnapshotStore snapshotStore;
    private final SourceAvailabilityService availabilityService;

    @Override
    public MonitoringSourceType getSourceType() {
        return MonitoringSourceType.ZABBIX;
    }

    @Override
    public void refresh() {
        refreshHosts();
        refreshProblems();
        refreshMetrics();
    }

    @Override
    public void refreshHosts() {
        String source = getSourceType().name();
        try {
            List<ServiceStatusDTO> statuses = List.copyOf(zabbixAdapter.fetchAll());
            serviceStatusPersistenceService.saveAll(statuses);
            saveSnapshot(
                    DATASET_HOSTS,
                    source,
                    statuses.stream().map(monitoringMapper::toHostFromServiceStatus).toList()
            );
        } catch (Exception exception) {
            handleRefreshFailure(DATASET_HOSTS, source, exception);
        }
    }

    @Override
    public void refreshProblems() {
        String source = getSourceType().name();
        try {
            List<ZabbixProblemDTO> problems = List.copyOf(zabbixProblemService.synchronizeActiveProblemsFromZabbix());
            saveSnapshot(
                    DATASET_PROBLEMS,
                    source,
                    problems.stream().map(monitoringMapper::toProblem).toList()
            );
        } catch (Exception exception) {
            handleRefreshFailure(DATASET_PROBLEMS, source, exception);
        }
    }

    @Override
    public void refreshMetrics() {
        String source = getSourceType().name();
        try {
            List<ZabbixMetric> metrics = List.copyOf(zabbixMetricsService.fetchAndSaveMetrics());
            saveSnapshot(
                    DATASET_METRICS,
                    source,
                    metrics.stream().map(monitoringMapper::toMetric).toList()
            );
        } catch (Exception exception) {
            handleRefreshFailure(DATASET_METRICS, source, exception);
        }
    }

    private <T> void refreshDataset(String dataset, Supplier<List<T>> loader) {
        String source = getSourceType().name();

        try {
            List<T> data = List.copyOf(loader.get());
            saveSnapshot(dataset, source, data);
        } catch (Exception exception) {
            handleRefreshFailure(dataset, source, exception);
        }
    }

    private <T> void saveSnapshot(String dataset, String source, List<T> data) {
        snapshotStore.save(
                dataset,
                source,
                StoredSnapshot.of(data, false, Map.of(source, FRESHNESS_LIVE))
        );
        availabilityService.markAvailable(source);
        log.debug("Stored {} {} snapshot entries for {}", data.size(), dataset, source);
    }

    private void handleRefreshFailure(String dataset, String source, Exception exception) {
        snapshotStore.<List<?>>get(dataset, source)
                .ifPresentOrElse(existing -> {
                    snapshotStore.save(
                            dataset,
                            source,
                            new StoredSnapshot<>(existing.data(), true, Map.of(source, FRESHNESS_SNAPSHOT), Instant.now())
                    );
                    availabilityService.markDegraded(source, safeMessage(exception));
                    log.warn("Failed to refresh {} for {}. Keeping last snapshot: {}", dataset, source, safeMessage(exception));
                }, () -> {
                    availabilityService.markUnavailable(source, safeMessage(exception));
                    log.warn("Failed to refresh {} for {} and no snapshot is available: {}", dataset, source, safeMessage(exception));
                });
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() != null && !exception.getMessage().isBlank()
                ? exception.getMessage()
                : "Unknown integration error";
    }
}
