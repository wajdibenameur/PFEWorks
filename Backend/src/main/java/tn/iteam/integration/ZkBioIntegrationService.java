package tn.iteam.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.function.Supplier;
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
import tn.iteam.service.support.DatabasePersistenceGuard;
import tn.iteam.service.support.MonitoringFreshnessService;
import tn.iteam.service.support.MonitoringSnapshotPublicationService;
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
    private static final String FRESHNESS_LIVE = StoredSnapshot.FRESHNESS_LIVE;
    private static final String FRESHNESS_MEMORY_SNAPSHOT = StoredSnapshot.FRESHNESS_MEMORY_SNAPSHOT_FALLBACK;
    private static final String FRESHNESS_SNAPSHOT_MISSING = StoredSnapshot.FRESHNESS_SNAPSHOT_MISSING;

    private final ZkBioServiceInterface zkBioService;
    private final tn.iteam.adapter.zkbio.ZkBioAdapter zkBioAdapter;
    private final ZkBioMonitoringMapper monitoringMapper;
    private final ServiceStatusPersistenceService serviceStatusPersistenceService;
    private final ZkBioPersistenceService zkBioPersistenceService;
    private final SnapshotStore snapshotStore;
    private final SourceAvailabilityService availabilityService;
    private final MonitoringSnapshotPublicationService monitoringSnapshotPublicationService;
    private final ZkBioWebSocketPublisher zkBioWebSocketPublisher;
    private final MonitoredHostPersistenceService monitoredHostPersistenceService;
    private final MonitoredHostSnapshotService monitoredHostSnapshotService;
    private final ZkBioProblemRepository zkBioProblemRepository;
    private final ZkBioMetricRepository zkBioMetricRepository;
    private final MonitoringFreshnessService freshnessService;
    private final DatabasePersistenceGuard databasePersistenceGuard;

    @Value("${app.monitoring.hosts.freshness-ms:300000}")
    private long hostsFreshnessMs;
    @Value("${app.monitoring.metrics.freshness-ms:60000}")
    private long metricsFreshnessMs;
    @Value("${app.monitoring.problems.freshness-ms:60000}")
    private long problemsFreshnessMs;
    @Value("${app.monitoring.source-health.freshness-ms:60000}")
    private long sourceHealthFreshnessMs;

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
        if (freshnessService.shouldSkipFetch(DATASET_HOSTS, source, hostsFreshnessMs)) {
            log.debug("FETCH SKIPPED fresh cache dataset={} source={}", DATASET_HOSTS, source);
            return;
        }
        try {
            // Step 1: Fetch live data
            List<ServiceStatusDTO> statuses = List.copyOf(zkBioAdapter.fetchAll());
            
            // Step 2: Save snapshot FIRST (always succeeds, in-memory)
            saveSnapshot(
                    DATASET_HOSTS,
                    source,
                    statuses.stream().map(monitoringMapper::toHost).toList()
            );
            
            // Step 3: Try DB persistence (non-blocking, wrapped)
            tryPersistToDatabase(source, DATASET_HOSTS, () -> {
                if (!freshnessService.hasPersistDelta(DATASET_HOSTS, source, statuses)) {
                    log.debug("PERSIST SKIPPED no changes dataset={} source={}", DATASET_HOSTS, source);
                    return;
                }
                serviceStatusPersistenceService.saveAll(statuses);
                monitoredHostPersistenceService.saveAll(source, statuses);
                freshnessService.markPersistSuccess(DATASET_HOSTS, source, statuses);
            });
            freshnessService.markFetchSuccess(DATASET_HOSTS, source);
        } catch (Exception exception) {
            handleRefreshFailure(DATASET_HOSTS, source, exception);
        }
    }

    @Override
    public void refreshProblems() {
        String source = getSourceType().name();
        if (freshnessService.shouldSkipFetch(DATASET_PROBLEMS, source, problemsFreshnessMs)) {
            log.debug("FETCH SKIPPED fresh cache dataset={} source={}", DATASET_PROBLEMS, source);
            return;
        }
        try {
            // Step 1: Fetch live data
            List<ZkBioProblemDTO> problems = List.copyOf(zkBioAdapter.fetchProblems());
            
            // Step 2: Save snapshot FIRST (always succeeds, in-memory)
            saveSnapshot(
                    DATASET_PROBLEMS,
                    source,
                    problems.stream().map(monitoringMapper::toProblem).toList()
            );
            
            // Step 3: Try DB persistence (non-blocking, wrapped)
            tryPersistToDatabase(source, DATASET_PROBLEMS, () -> {
                if (!freshnessService.hasPersistDelta(DATASET_PROBLEMS, source, problems)) {
                    log.debug("PERSIST SKIPPED no changes dataset={} source={}", DATASET_PROBLEMS, source);
                    return;
                }
                zkBioPersistenceService.saveProblems(problems);
                freshnessService.markPersistSuccess(DATASET_PROBLEMS, source, problems);
            });
            freshnessService.markFetchSuccess(DATASET_PROBLEMS, source);
        } catch (Exception exception) {
            handleRefreshFailure(DATASET_PROBLEMS, source, exception);
        }
    }

    @Override
    public void refreshMetrics() {
        subscribeSafely("refreshMetrics", refreshMetricsAsync());
    }

    public Mono<Void> refreshAsync() {
        return refreshHostsAsync()
                .then(refreshProblemsAsync())
                .then(refreshMetricsAsync());
    }

    public Mono<Void> refreshHostsAsync() {
        String source = getSourceType().name();
        if (freshnessService.shouldSkipFetch(DATASET_HOSTS, source, hostsFreshnessMs)) {
            log.debug("FETCH SKIPPED fresh cache dataset={} source={}", DATASET_HOSTS, source);
            return Mono.empty();
        }
        return Mono.fromCallable(zkBioAdapter::fetchAll)
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(statuses -> {
                List<ServiceStatusDTO> statusList = List.copyOf(statuses);
                saveSnapshot(DATASET_HOSTS, source, statusList.stream().map(monitoringMapper::toHost).toList());
                tryPersistToDatabase(source, DATASET_HOSTS, () -> {
                    if (!freshnessService.hasPersistDelta(DATASET_HOSTS, source, statusList)) {
                        log.debug("PERSIST SKIPPED no changes dataset={} source={}", DATASET_HOSTS, source);
                        return;
                    }
                    serviceStatusPersistenceService.saveAll(statusList);
                    monitoredHostPersistenceService.saveAll(source, statusList);
                    freshnessService.markPersistSuccess(DATASET_HOSTS, source, statusList);
                });
                freshnessService.markFetchSuccess(DATASET_HOSTS, source);
            })
            .onErrorResume(throwable -> {
                handleRefreshFailure(DATASET_HOSTS, source, toException(throwable));
                return Mono.empty();
            })
            .then();
    }

    public Mono<Void> refreshProblemsAsync() {
        String source = getSourceType().name();
        if (freshnessService.shouldSkipFetch(DATASET_PROBLEMS, source, problemsFreshnessMs)) {
            log.debug("FETCH SKIPPED fresh cache dataset={} source={}", DATASET_PROBLEMS, source);
            return Mono.empty();
        }
        return Mono.fromCallable(zkBioAdapter::fetchProblems)
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(problems -> {
                List<ZkBioProblemDTO> problemList = List.copyOf(problems);
                saveSnapshot(DATASET_PROBLEMS, source, problemList.stream().map(monitoringMapper::toProblem).toList());
                tryPersistToDatabase(source, DATASET_PROBLEMS, () -> {
                    if (!freshnessService.hasPersistDelta(DATASET_PROBLEMS, source, problemList)) {
                        log.debug("PERSIST SKIPPED no changes dataset={} source={}", DATASET_PROBLEMS, source);
                        return;
                    }
                    zkBioPersistenceService.saveProblems(problemList);
                    freshnessService.markPersistSuccess(DATASET_PROBLEMS, source, problemList);
                });
                freshnessService.markFetchSuccess(DATASET_PROBLEMS, source);
            })
            .onErrorResume(throwable -> {
                handleRefreshFailure(DATASET_PROBLEMS, source, toException(throwable));
                return Mono.empty();
            })
            .then();
    }

    public Mono<Void> refreshMetricsAsync() {
        String source = getSourceType().name();
        if (freshnessService.shouldSkipFetch(DATASET_METRICS, source, metricsFreshnessMs)) {
            log.debug("FETCH SKIPPED fresh cache dataset={} source={}", DATASET_METRICS, source);
            return Mono.empty();
        }
        return Mono.fromCallable(() -> {
                    // Step 1: Fetch live data
                    List<ZkBioMetricDTO> metrics = List.copyOf(zkBioAdapter.fetchMetrics());
                    
                    // Step 2: Save snapshot FIRST (always succeeds, in-memory)
                    List<?> data = metrics.stream().map(monitoringMapper::toMetric).toList();
                    
                    // Step 3: Try DB persistence (non-blocking, wrapped)
                    tryPersistToDatabase(source, DATASET_METRICS, () -> {
                        if (!freshnessService.hasPersistDelta(DATASET_METRICS, source, metrics)) {
                            log.debug("PERSIST SKIPPED no changes dataset={} source={}", DATASET_METRICS, source);
                            return;
                        }
                        zkBioPersistenceService.saveMetrics(metrics);
                        freshnessService.markPersistSuccess(DATASET_METRICS, source, metrics);
                    });
                    freshnessService.markFetchSuccess(DATASET_METRICS, source);
                    
                    return data;
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
        return refreshRawDatasetAsync(DATASET_STATUS, () -> Mono.fromCallable(zkBioService::getServerStatus))
                .then(refreshRawDatasetAsync(DATASET_DEVICES, () -> Mono.fromCallable(zkBioService::fetchDevices)))
                .then(refreshRawDatasetAsync(
                        DATASET_ATTENDANCE,
                        () -> Mono.fromCallable(() -> zkBioService.fetchAttendanceLogs(0L, System.currentTimeMillis()))
                ));
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
                    monitoringSnapshotPublicationService.publishProblemsSnapshot(MonitoringSourceType.ZKBIO);
                    monitoringSnapshotPublicationService.publishMetricsSnapshot(MonitoringSourceType.ZKBIO);
                    zkBioWebSocketPublisher.publishAttendanceFromSnapshot();
                    zkBioWebSocketPublisher.publishDevicesFromSnapshot();
                    zkBioWebSocketPublisher.publishStatusFromSnapshot();
                }));
    }

    private <T> Mono<Void> refreshRawDatasetAsync(String dataset, Supplier<Mono<T>> loader) {
        String source = getSourceType().name();
        if (freshnessService.shouldSkipFetch(dataset, source, sourceHealthFreshnessMs)) {
            log.debug("FETCH SKIPPED fresh cache dataset={} source={}", dataset, source);
            return Mono.empty();
        }
        return Mono.defer(loader::get)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(data -> {
                    snapshotStore.save(
                            dataset,
                            source,
                            StoredSnapshot.of(data, false, Map.of(source, FRESHNESS_LIVE))
                    );
                    availabilityService.markAvailable(source);
                    freshnessService.markFetchSuccess(dataset, source);
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
        // Try in-memory snapshot first (always available)
        Object existingSnapshot = safeGetExistingSnapshot(dataset, source).orElse(null);
        if (existingSnapshot != null) {
            saveFallbackSnapshot(dataset, source, existingSnapshot);
            availabilityService.markDegraded(source, safeMessage(exception));
            log.warn("Failed to refresh {} for {}. Serving snapshot_fallback from in-memory: {}", 
                    dataset, source, safeMessage(exception));
            return;
        }

        // Skip DB fallback when DB is down - return empty immediately
        // DO NOT try to load from DB as it will block and cause timeouts
        saveFallbackSnapshot(dataset, source, List.of());
        availabilityService.markUnavailable(source, safeMessage(exception));
        log.warn("Failed to refresh {} for {}. No snapshot available, serving empty: {}", 
                dataset, source, safeMessage(exception));
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
        return databasePersistenceGuard.safeLoad(
                getSourceType().name(),
                dataset + "-fallback-load",
                () -> loadPersistedFallback(dataset),
                null
        );
    }

    private void saveFallbackSnapshot(String dataset, String source, Object data) {
        try {
            snapshotStore.save(
                    dataset,
                    source,
                    new StoredSnapshot<>(
                            data,
                            true,
                            Map.of(
                                    source,
                                    (data instanceof java.util.List<?> list && list.isEmpty())
                                            ? FRESHNESS_SNAPSHOT_MISSING
                                            : FRESHNESS_MEMORY_SNAPSHOT
                            ),
                            Instant.now()
                    )
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

    private boolean tryPersistToDatabase(String source, String dataset, Runnable persistenceAction) {
        long startedAt = System.currentTimeMillis();
        boolean persisted = databasePersistenceGuard.safeRun(source, dataset + "-persistence", persistenceAction);
        if (persisted) {
            log.info("ZKBio DB persistence done durationMs={}", System.currentTimeMillis() - startedAt);
        }
        return persisted;
    }

    private Exception toException(Throwable throwable) {
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(throwable);
    }

}
