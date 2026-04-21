package tn.iteam.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZkBioMetricDTO;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.mapper.ZkBioMonitoringMapper;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.snapshot.SnapshotStore;
import tn.iteam.monitoring.snapshot.StoredSnapshot;
import tn.iteam.service.ServiceStatusPersistenceService;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.ZkBioPersistenceService;
import tn.iteam.service.ZkBioServiceInterface;
import tn.iteam.websocket.MonitoringWebSocketPublisher;
import tn.iteam.websocket.ZkBioWebSocketPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZkBioIntegrationService implements IntegrationService {

    private static final String DATASET_ATTENDANCE = "attendance";
    private static final String DATASET_DEVICES = "devices";
    private static final String DATASET_HOSTS = "hosts";
    private static final String DATASET_METRICS = "metrics";
    private static final String DATASET_PROBLEMS = "problems";
    private static final String DATASET_STATUS = "status";
    private static final String FRESHNESS_LIVE = "live";
    private static final String FRESHNESS_SNAPSHOT = "snapshot_fallback";

    private final ZkBioServiceInterface zkBioService;
    private final tn.iteam.adapter.zkbio.ZkBioAdapter zkBioAdapter;
    private final ZkBioMonitoringMapper monitoringMapper;
    private final ServiceStatusPersistenceService serviceStatusPersistenceService;
    private final ZkBioPersistenceService zkBioPersistenceService;
    private final SnapshotStore snapshotStore;
    private final SourceAvailabilityService availabilityService;
    private final MonitoringWebSocketPublisher monitoringWebSocketPublisher;
    private final ZkBioWebSocketPublisher zkBioWebSocketPublisher;

    @Override
    public MonitoringSourceType getSourceType() {
        return MonitoringSourceType.ZKBIO;
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
            List<ServiceStatusDTO> statuses = List.copyOf(zkBioAdapter.fetchAll());
            serviceStatusPersistenceService.saveAll(statuses);
            saveSnapshot(
                    DATASET_HOSTS,
                    source,
                    statuses.stream().map(monitoringMapper::toHost).toList()
            );
        } catch (Exception exception) {
            handleRefreshFailure(DATASET_HOSTS, source, exception);
        }
    }

    @Override
    public void refreshProblems() {
        String source = getSourceType().name();
        try {
            List<ZkBioProblemDTO> problems = List.copyOf(zkBioAdapter.fetchProblems());
            zkBioPersistenceService.saveProblems(problems);
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
            List<ZkBioMetricDTO> metrics = List.copyOf(zkBioAdapter.fetchMetrics());
            zkBioPersistenceService.saveMetrics(metrics);
            saveSnapshot(
                    DATASET_METRICS,
                    source,
                    metrics.stream().map(monitoringMapper::toMetric).toList()
            );
        } catch (Exception exception) {
            handleRefreshFailure(DATASET_METRICS, source, exception);
        }
    }
    @Override
    public void refreshAttendance() {
        refreshRawDataset(DATASET_STATUS, zkBioService::getServerStatus);
        refreshRawDataset(DATASET_DEVICES, zkBioService::fetchDevices);
        refreshRawDataset(DATASET_ATTENDANCE, zkBioService::fetchAttendanceLogs);
    }

    @Override
    @Async
    public void refreshAllAndPublish() {
        log.info("Triggering manual ZKBio integration refresh and snapshot publication");

        refresh();
        refreshAttendance();

        monitoringWebSocketPublisher.publishProblemsFromSnapshot(MonitoringSourceType.ZKBIO);
        monitoringWebSocketPublisher.publishMetricsFromSnapshot(MonitoringSourceType.ZKBIO);
        zkBioWebSocketPublisher.publishAttendanceFromSnapshot();
        zkBioWebSocketPublisher.publishDevicesFromSnapshot();
        zkBioWebSocketPublisher.publishStatusFromSnapshot();
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

    private <T> void refreshRawDataset(String dataset, Supplier<T> loader) {
        String source = getSourceType().name();

        try {
            T data = loader.get();
            snapshotStore.save(
                    dataset,
                    source,
                    StoredSnapshot.of(data, false, Map.of(source, FRESHNESS_LIVE))
            );
            availabilityService.markAvailable(source);
            log.debug("Stored {} snapshot for {}", dataset, source);
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
        snapshotStore.<Object>get(dataset, source)
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