package tn.iteam.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZkBioMetricDTO;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.mapper.ZkBioMonitoringMapper;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.snapshot.SnapshotStore;
import tn.iteam.monitoring.snapshot.StoredSnapshot;
import tn.iteam.repository.ZkBioMetricRepository;
import tn.iteam.repository.ZkBioProblemRepository;
import tn.iteam.service.MonitoredHostPersistenceService;
import tn.iteam.service.MonitoredHostSnapshotService;
import tn.iteam.service.ServiceStatusPersistenceService;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.ZkBioPersistenceService;
import tn.iteam.service.ZkBioServiceInterface;
import tn.iteam.websocket.MonitoringWebSocketPublisher;
import tn.iteam.websocket.ZkBioWebSocketPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZkBioIntegrationService implements ZkBioIntegrationOperations {

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
    private final MonitoredHostPersistenceService monitoredHostPersistenceService;
    private final MonitoredHostSnapshotService monitoredHostSnapshotService;
    private final ZkBioProblemRepository zkBioProblemRepository;
    private final ZkBioMetricRepository zkBioMetricRepository;

    @Override
    public MonitoringSourceType getSourceType() {
        return MonitoringSourceType.ZKBIO;
    }

    @Override
    public void refresh() {
        subscribeSafely("refresh", refreshAsync());
    }

    @Override
    public void refreshHosts() {
        String source = getSourceType().name();
        try {
            List<ServiceStatusDTO> statuses = List.copyOf(zkBioAdapter.fetchAll());
            serviceStatusPersistenceService.saveAll(statuses);
            monitoredHostPersistenceService.saveAll(source, statuses);
            saveSnapshot(
                    DATASET_HOSTS,
                    source,
                    monitoredHostSnapshotService.loadHosts(getSourceType())
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
        subscribeSafely("refreshMetrics", refreshMetricsAsync());
    }

    public Mono<Void> refreshAsync() {
        return runStepAsync("refreshHosts", this::refreshHosts)
                .then(runStepAsync("refreshProblems", this::refreshProblems))
                .then(refreshMetricsAsync());
    }

    public Mono<Void> refreshMetricsAsync() {
        String source = getSourceType().name();
        return Mono.fromCallable(() -> {
                    List<ZkBioMetricDTO> metrics = List.copyOf(zkBioAdapter.fetchMetrics());
                    zkBioPersistenceService.saveMetrics(metrics);
                    return metrics.stream().map(monitoringMapper::toMetric).toList();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(data -> saveSnapshot(DATASET_METRICS, source, data))
                .onErrorResume(throwable -> {
                    handleRefreshFailure(DATASET_METRICS, source, toException(throwable));
                    return Mono.empty();
                })
                .then();
    }
    @Override
    public void refreshAttendance() {
        subscribeSafely("refreshAttendance", refreshAttendanceAsync());
    }

    public Mono<Void> refreshAttendanceAsync() {
        return refreshRawDatasetAsync(DATASET_STATUS, zkBioService::getServerStatus)
                .then(refreshRawDatasetAsync(DATASET_DEVICES, zkBioService::fetchDevices))
                .then(refreshRawDatasetAsync(DATASET_ATTENDANCE, zkBioService::fetchAttendanceLogs));
    }

    @Override
    @Async
    public void refreshAllAndPublish() {
        subscribeSafely("refreshAllAndPublish", refreshAllAndPublishAsync());
    }

    public Mono<Void> refreshAllAndPublishAsync() {
        log.info("Triggering manual ZKBio integration refresh and snapshot publication");
        return refreshAsync()
                .then(refreshAttendanceAsync())
                .then(Mono.fromRunnable(() -> {
                    monitoringWebSocketPublisher.publishProblemsFromSnapshot(MonitoringSourceType.ZKBIO);
                    monitoringWebSocketPublisher.publishMetricsFromSnapshot(MonitoringSourceType.ZKBIO);
                    zkBioWebSocketPublisher.publishAttendanceFromSnapshot();
                    zkBioWebSocketPublisher.publishDevicesFromSnapshot();
                    zkBioWebSocketPublisher.publishStatusFromSnapshot();
                }));
    }

    private <T> Mono<Void> refreshRawDatasetAsync(String dataset, java.util.function.Supplier<T> loader) {
        String source = getSourceType().name();
        return Mono.fromCallable(loader::get)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(data -> {
                    snapshotStore.save(
                            dataset,
                            source,
                            StoredSnapshot.of(data, false, Map.of(source, FRESHNESS_LIVE))
                    );
                    availabilityService.markAvailable(source);
                    log.debug("Stored {} snapshot for {}", dataset, source);
                })
                .onErrorResume(throwable -> {
                    handleRefreshFailure(dataset, source, toException(throwable));
                    return Mono.empty();
                })
                .then();
    }

    private <T> void saveSnapshot(String dataset, String source, List<T> data) {
        try {
            snapshotStore.save(
                    dataset,
                    source,
                    StoredSnapshot.of(data, false, Map.of(source, FRESHNESS_LIVE))
            );
            availabilityService.markAvailable(source);
            log.debug("Stored {} {} snapshot entries for {}", data.size(), dataset, source);
        } catch (Exception exception) {
            log.warn("Unable to store {} snapshot for {}: {}", dataset, source, safeMessage(exception));
        }
    }

    private void handleRefreshFailure(String dataset, String source, Exception exception) {
        Object existingSnapshot = safeGetExistingSnapshot(dataset, source).orElse(null);
        if (existingSnapshot != null) {
            saveFallbackSnapshot(dataset, source, existingSnapshot);
            availabilityService.markDegraded(source, safeMessage(exception));
            log.warn("Failed to refresh {} for {}. Keeping last snapshot: {}", dataset, source, safeMessage(exception));
            return;
        }

        Object persistedFallback = safeLoadPersistedFallback(dataset);
        if (hasPersistedFallback(persistedFallback)) {
            saveFallbackSnapshot(dataset, source, persistedFallback);
            availabilityService.markDegraded(source, safeMessage(exception));
            log.warn(
                    "Failed to refresh {} for {}. Rebuilt snapshot from persisted data ({} entries): {}",
                    dataset,
                    source,
                    fallbackSize(persistedFallback),
                    safeMessage(exception)
            );
            return;
        }

        saveFallbackSnapshot(dataset, source, List.of());
        availabilityService.markUnavailable(source, safeMessage(exception));
        log.warn("Failed to refresh {} for {}. Serving empty snapshot fallback: {}", dataset, source, safeMessage(exception));
    }

    private Object loadPersistedFallback(String dataset) {
        return switch (dataset) {
            case DATASET_HOSTS -> monitoredHostSnapshotService.loadHosts(getSourceType());
            case DATASET_PROBLEMS -> zkBioProblemRepository.findByActiveTrue().stream()
                    .map(problem -> monitoringMapper.toProblem(ZkBioProblemDTO.builder()
                            .problemId(problem.getProblemId())
                            .host(problem.getDevice())
                            .description(problem.getDescription())
                            .active(Boolean.TRUE.equals(problem.getActive()))
                            .status(problem.getStatus())
                            .startedAt(problem.getStartedAt())
                            .resolvedAt(problem.getResolvedAt())
                            .eventId(problem.getEventId())
                            .build()))
                    .toList();
            case DATASET_METRICS -> zkBioMetricRepository.findAll().stream()
                    .map(metric -> monitoringMapper.toMetric(ZkBioMetricDTO.builder()
                            .hostId(metric.getHostId())
                            .hostName(metric.getHostName())
                            .itemId(metric.getItemId())
                            .metricKey(metric.getMetricKey())
                            .value(metric.getValue())
                            .timestamp(metric.getTimestamp())
                            .ip(metric.getIp())
                            .port(metric.getPort())
                            .build()))
                    .toList();
            default -> null;
        };
    }

    private boolean hasPersistedFallback(Object persistedFallback) {
        if (persistedFallback instanceof List<?> list) {
            return !list.isEmpty();
        }
        return persistedFallback != null;
    }

    private int fallbackSize(Object persistedFallback) {
        if (persistedFallback instanceof List<?> list) {
            return list.size();
        }
        return persistedFallback == null ? 0 : 1;
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() != null && !exception.getMessage().isBlank()
                ? exception.getMessage()
                : "Unknown integration error";
    }

    private Mono<Void> runStepAsync(String operation, Runnable action) {
        return Mono.fromRunnable(action)
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .onErrorResume(throwable -> {
                    log.warn("ZKBio {} async step failed but application remains available: {}", operation, safeMessage(throwable));
                    return Mono.<Void>empty();
                });
    }

    private void subscribeSafely(String operation, Mono<Void> pipeline) {
        pipeline.subscribe(
                unused -> {
                },
                throwable -> log.warn("ZKBio {} async failed but application remains available: {}", operation, safeMessage(throwable))
        );
    }

    private Optional<Object> safeGetExistingSnapshot(String dataset, String source) {
        try {
            return snapshotStore.<Object>get(dataset, source).map(StoredSnapshot::data);
        } catch (Exception exception) {
            log.warn("Unable to read existing {} snapshot for {}: {}", dataset, source, safeMessage(exception));
            return Optional.empty();
        }
    }

    private Object safeLoadPersistedFallback(String dataset) {
        try {
            return loadPersistedFallback(dataset);
        } catch (Exception exception) {
            log.warn("Unable to load persisted {} fallback: {}", dataset, safeMessage(exception));
            return null;
        }
    }

    private void saveFallbackSnapshot(String dataset, String source, Object data) {
        try {
            snapshotStore.save(
                    dataset,
                    source,
                    new StoredSnapshot<>(data, true, Map.of(source, FRESHNESS_SNAPSHOT), Instant.now())
            );
        } catch (Exception snapshotException) {
            log.warn("Unable to save fallback {} snapshot for {}: {}", dataset, source, safeMessage(snapshotException));
        }
    }

    private String safeMessage(Throwable throwable) {
        return throwable.getMessage() != null && !throwable.getMessage().isBlank()
                ? throwable.getMessage()
                : "Unknown integration error";
    }

    private Exception toException(Throwable throwable) {
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(throwable);
    }

}
