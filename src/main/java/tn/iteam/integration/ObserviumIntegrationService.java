package tn.iteam.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.observium.ObserviumAdapter;
import tn.iteam.dto.ObserviumMetricDTO;
import tn.iteam.dto.ObserviumProblemDTO;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.mapper.ObserviumMonitoringMapper;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.snapshot.SnapshotStore;
import tn.iteam.monitoring.snapshot.StoredSnapshot;
import tn.iteam.service.ObserviumPersistenceService;
import tn.iteam.service.ServiceStatusPersistenceService;
import tn.iteam.service.SourceAvailabilityService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class ObserviumIntegrationService implements IntegrationService {

    private static final String DATASET_HOSTS = "hosts";
    private static final String DATASET_METRICS = "metrics";
    private static final String DATASET_PROBLEMS = "problems";
    private static final String FRESHNESS_LIVE = "live";
    private static final String FRESHNESS_SNAPSHOT = "snapshot_fallback";

    private final ObserviumAdapter observiumAdapter;
    private final ObserviumMonitoringMapper monitoringMapper;
    private final ObserviumPersistenceService observiumPersistenceService;
    private final ServiceStatusPersistenceService serviceStatusPersistenceService;
    private final SnapshotStore snapshotStore;
    private final SourceAvailabilityService availabilityService;

    @Override
    public MonitoringSourceType getSourceType() {
        return MonitoringSourceType.OBSERVIUM;
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
            List<ServiceStatusDTO> statuses = List.copyOf(observiumAdapter.fetchAll());
            serviceStatusPersistenceService.saveAll(statuses);
            List<UnifiedMonitoringHostDTO> data = monitoringMapper.toHosts(statuses);
            saveSnapshot(DATASET_HOSTS, source, data);
        } catch (Exception exception) {
            handleRefreshFailure(DATASET_HOSTS, source, exception);
        }
    }

    @Override
    public void refreshProblems() {
        String source = getSourceType().name();
        try {
            List<ObserviumProblemDTO> problems = List.copyOf(observiumAdapter.fetchProblems());
            observiumPersistenceService.saveProblems(problems);
            List<UnifiedMonitoringProblemDTO> data = monitoringMapper.toProblems(problems);
            saveSnapshot(DATASET_PROBLEMS, source, data);
        } catch (Exception exception) {
            handleRefreshFailure(DATASET_PROBLEMS, source, exception);
        }
    }

    @Override
    public void refreshMetrics() {
        String source = getSourceType().name();
        try {
            List<ObserviumMetricDTO> metrics = List.copyOf(observiumAdapter.fetchMetrics());
            observiumPersistenceService.saveMetrics(metrics);
            List<UnifiedMonitoringMetricDTO> data = metrics.stream()
                    .map(monitoringMapper::toMetric)
                    .toList();
            saveSnapshot(DATASET_METRICS, source, data);
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
