package tn.iteam.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tn.iteam.dto.SnmpMetricDTO;
import tn.iteam.dto.SnmpProblemDTO;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.mapper.SnmpMonitoringMapper;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.snapshot.SnapshotStore;
import tn.iteam.monitoring.snapshot.StoredSnapshot;
import tn.iteam.repository.SnmpMetricRepository;
import tn.iteam.repository.SnmpProblemRepository;
import tn.iteam.service.MonitoredHostPersistenceService;
import tn.iteam.service.MonitoredHostSnapshotService;
import tn.iteam.service.SnmpPersistenceService;
import tn.iteam.service.SnmpSourceService;
import tn.iteam.service.ServiceStatusPersistenceService;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.support.DatabasePersistenceGuard;
import tn.iteam.service.support.MonitoringFreshnessService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnmpIntegrationService implements AsyncIntegrationService {

    private static final String DATASET_HOSTS = "hosts";
    private static final String DATASET_METRICS = "metrics";
    private static final String DATASET_PROBLEMS = "problems";
    private static final String FRESHNESS_LIVE = StoredSnapshot.FRESHNESS_LIVE;
    private static final String FRESHNESS_MEMORY_SNAPSHOT = StoredSnapshot.FRESHNESS_MEMORY_SNAPSHOT_FALLBACK;
    private static final String FRESHNESS_DATABASE_SNAPSHOT = StoredSnapshot.FRESHNESS_DATABASE_SNAPSHOT_FALLBACK;
    private static final String FRESHNESS_SNAPSHOT_MISSING = StoredSnapshot.FRESHNESS_SNAPSHOT_MISSING;

    private final SnmpSourceService snmpSourceService;
    private final SnmpMonitoringMapper monitoringMapper;
    private final SnmpPersistenceService snmpPersistenceService;
    private final ServiceStatusPersistenceService serviceStatusPersistenceService;
    private final SnapshotStore snapshotStore;
    private final SourceAvailabilityService availabilityService;
    private final MonitoredHostPersistenceService monitoredHostPersistenceService;
    private final MonitoredHostSnapshotService monitoredHostSnapshotService;
    private final SnmpProblemRepository snmpProblemRepository;
    private final SnmpMetricRepository snmpMetricRepository;
    private final MonitoringFreshnessService freshnessService;
    private final DatabasePersistenceGuard databasePersistenceGuard;

    @Value("${app.monitoring.hosts.freshness-ms:300000}")
    private long hostsFreshnessMs;
    @Value("${app.monitoring.metrics.freshness-ms:60000}")
    private long metricsFreshnessMs;
    @Value("${app.monitoring.problems.freshness-ms:60000}")
    private long problemsFreshnessMs;

    @PostConstruct
    void hydrateSnapshotsFromDatabaseOnStartup() {
        String source = getSourceType().name();
        hydrateDatasetIfMissing(DATASET_HOSTS, source);
        hydrateDatasetIfMissing(DATASET_PROBLEMS, source);
        hydrateDatasetIfMissing(DATASET_METRICS, source);
    }

    @Override
    public MonitoringSourceType getSourceType() {
        return MonitoringSourceType.SNMP;
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
            List<ServiceStatusDTO> statuses = List.copyOf(snmpSourceService.fetchAll());
            
            // Step 2: Save snapshot FIRST (always succeeds, in-memory)
            List<UnifiedMonitoringHostDTO> data = monitoringMapper.toHosts(statuses);
            saveSnapshot(DATASET_HOSTS, source, data);
            
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
            List<SnmpProblemDTO> problems = List.copyOf(snmpSourceService.fetchProblems());

            // Step 2: Try DB persistence FIRST to preserve stable startedAt/lastObservedAt
            boolean persisted = tryPersistToDatabase(source, DATASET_PROBLEMS, () -> {
                if (!freshnessService.hasPersistDelta(DATASET_PROBLEMS, source, problems)) {
                    log.debug("PERSIST SKIPPED no changes dataset={} source={}", DATASET_PROBLEMS, source);
                    return;
                }
                snmpPersistenceService.saveProblems(problems);
                freshnessService.markPersistSuccess(DATASET_PROBLEMS, source, problems);
            });

            // Step 3: Save current snapshot from recent persisted problems when available
            saveSnapshot(DATASET_PROBLEMS, source, persisted
                    ? loadRecentProblemSnapshotData()
                    : problems.stream().map(monitoringMapper::toProblem).toList());
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
        return Mono.fromCallable(snmpSourceService::fetchAll)
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
        return Mono.fromCallable(snmpSourceService::fetchProblems)
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(problems -> {
                List<SnmpProblemDTO> problemList = List.copyOf(problems);
                boolean persisted = tryPersistToDatabase(source, DATASET_PROBLEMS, () -> {
                    if (!freshnessService.hasPersistDelta(DATASET_PROBLEMS, source, problemList)) {
                        log.debug("PERSIST SKIPPED no changes dataset={} source={}", DATASET_PROBLEMS, source);
                        return;
                    }
                    snmpPersistenceService.saveProblems(problemList);
                    freshnessService.markPersistSuccess(DATASET_PROBLEMS, source, problemList);
                });
                saveSnapshot(DATASET_PROBLEMS, source, persisted
                        ? loadRecentProblemSnapshotData()
                        : problemList.stream().map(monitoringMapper::toProblem).toList());
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
        return Mono.fromCallable(snmpSourceService::fetchMetrics)
                .subscribeOn(Schedulers.boundedElastic())
                .map(metrics -> {
                    List<SnmpMetricDTO> metricList = List.copyOf(metrics);
                    List<UnifiedMonitoringMetricDTO> data = metricList.stream().map(monitoringMapper::toMetric).toList();
                    tryPersistToDatabase(source, DATASET_METRICS, () -> {
                        if (!freshnessService.hasPersistDelta(DATASET_METRICS, source, metricList)) {
                            log.debug("PERSIST SKIPPED no changes dataset={} source={}", DATASET_METRICS, source);
                            return;
                        }
                        snmpPersistenceService.saveMetrics(metricList);
                        freshnessService.markPersistSuccess(DATASET_METRICS, source, metricList);
                    });
                    freshnessService.markFetchSuccess(DATASET_METRICS, source);
                    return data;
                })
                .doOnNext(data -> saveSnapshot(DATASET_METRICS, source, data))
                .onErrorResume(throwable -> {
                    handleRefreshFailure(DATASET_METRICS, source, toException(throwable));
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
        snapshotStore.<List<?>>get(dataset, source)
                .ifPresentOrElse(existing -> {
                    snapshotStore.save(
                            dataset,
                            source,
                            new StoredSnapshot<>(existing.data(), true, Map.of(source, FRESHNESS_MEMORY_SNAPSHOT), Instant.now())
                    );
                    availabilityService.markDegraded(source, safeMessage(exception));
                    log.warn("Failed to refresh {} for {}. Keeping last in-memory snapshot: {}",
                            dataset, source, safeMessage(exception));
                }, () -> {
                    List<?> persistedFallback = safeLoadPersistedFallback(dataset);

                    if (!persistedFallback.isEmpty()) {
                        snapshotStore.save(
                                dataset,
                                source,
                                new StoredSnapshot<>(persistedFallback, true, Map.of(source, FRESHNESS_DATABASE_SNAPSHOT), Instant.now())
                        );
                        availabilityService.markDegraded(source, safeMessage(exception));
                        log.warn("Failed to refresh {} for {}. Loaded fallback from persisted DB: {}",
                                dataset, source, safeMessage(exception));
                        return;
                    }

                    snapshotStore.save(
                            dataset,
                            source,
                            new StoredSnapshot<>(List.of(), true, Map.of(source, FRESHNESS_SNAPSHOT_MISSING), Instant.now())
                    );
                    availabilityService.markUnavailable(source, safeMessage(exception));
                    log.warn("Failed to refresh {} for {}. No memory snapshot and no persisted DB fallback: {}",
                            dataset, source, safeMessage(exception));
                });
    }

    private List<?> loadPersistedFallback(String dataset) {
        return switch (dataset) {
            case DATASET_HOSTS -> monitoredHostSnapshotService.loadHosts(getSourceType());
            case DATASET_PROBLEMS -> loadRecentProblemSnapshotData();
            case DATASET_METRICS -> snmpMetricRepository.findAll().stream()
                    .map(metric -> monitoringMapper.toMetric(SnmpMetricDTO.builder()
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
            default -> List.of();
        };
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() != null && !exception.getMessage().isBlank()
                ? exception.getMessage()
                : "Unknown integration error";
    }

    private String safeMessage(Throwable throwable) {
        return throwable.getMessage() != null && !throwable.getMessage().isBlank()
                ? throwable.getMessage()
                : "Unknown integration error";
    }

    private Mono<Void> runStepAsync(String operation, Runnable action) {
        return Mono.fromRunnable(action)
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .onErrorResume(throwable -> {
                    log.warn("SNMP {} async step failed but application remains available: {}", operation, safeMessage(throwable));
                    return Mono.<Void>empty();
                });
    }

    private void subscribeSafely(String operation, Mono<Void> pipeline) {
        pipeline.subscribe(
                unused -> {
                },
                throwable -> log.warn("SNMP {} async failed but application remains available: {}", operation, safeMessage(throwable))
        );
    }

    private Optional<List<?>> safeGetExistingSnapshot(String dataset, String source) {
        try {
            return snapshotStore.<List<?>>get(dataset, source).map(StoredSnapshot::data);
        } catch (Exception exception) {
            log.warn("Unable to read existing {} snapshot for {}: {}", dataset, source, safeMessage(exception));
            return Optional.empty();
        }
    }

    private List<?> safeLoadPersistedFallback(String dataset) {
        return databasePersistenceGuard.safeLoad(
                getSourceType().name(),
                dataset + "-fallback-load",
                () -> loadPersistedFallback(dataset),
                List.of()
        );
    }

    private List<UnifiedMonitoringProblemDTO> loadRecentProblemSnapshotData() {
        return snmpProblemRepository.findRecentActiveProblems(problemRecencyCutoffEpochSeconds()).stream()
                .map(problem -> monitoringMapper.toProblem(SnmpProblemDTO.builder()
                        .problemId(problem.getProblemId())
                        .hostId(problem.getHostId())
                        .host(problem.getDevice())
                        .description(problem.getDescription())
                        .severity(problem.getSeverity())
                        .active(Boolean.TRUE.equals(problem.getActive()))
                        .eventId(problem.getEventId())
                        .startedAt(problem.getStartedAt())
                        .lastObservedAt(problem.getLastObservedAt())
                        .resolvedAt(problem.getResolvedAt())
                        .build()))
                .toList();
    }

    private long problemRecencyCutoffEpochSeconds() {
        long recencyMs = Math.max(problemsFreshnessMs * 2, 10 * 60 * 1000L);
        return Instant.now().minusMillis(recencyMs).getEpochSecond();
    }

    private void hydrateDatasetIfMissing(String dataset, String source) {
        try {
            boolean hasSnapshot = snapshotStore.<List<?>>get(dataset, source).isPresent();
            if (hasSnapshot) {
                return;
            }

            List<?> persisted = safeLoadPersistedFallback(dataset);
            if (persisted.isEmpty()) {
                return;
            }

            snapshotStore.save(
                    dataset,
                    source,
                    new StoredSnapshot<>(persisted, true, Map.of(source, FRESHNESS_DATABASE_SNAPSHOT), Instant.now())
            );
            log.info("Hydrated {} snapshot from persisted DB for {} entries={}", dataset, source, persisted.size());
        } catch (Exception exception) {
            log.warn("Unable to hydrate {} snapshot from DB for {}: {}", dataset, source, safeMessage(exception));
        }
    }

    private void saveFallbackSnapshot(String dataset, String source, List<?> data) {
        try {
            snapshotStore.save(
                    dataset,
                    source,
                    new StoredSnapshot<>(data, true, Map.of(source, FRESHNESS_MEMORY_SNAPSHOT), Instant.now())
            );
        } catch (Exception snapshotException) {
            log.warn("Unable to save fallback {} snapshot for {}: {}", dataset, source, safeMessage(snapshotException));
        }
    }

    private boolean tryPersistToDatabase(String source, String dataset, Runnable persistenceAction) {
        long startedAt = System.currentTimeMillis();
        boolean persisted = databasePersistenceGuard.safeRun(source, dataset + "-persistence", persistenceAction);
        if (persisted) {
            log.info("SNMP DB persistence done durationMs={}", System.currentTimeMillis() - startedAt);
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
