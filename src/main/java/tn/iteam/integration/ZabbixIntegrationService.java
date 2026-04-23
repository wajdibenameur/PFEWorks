package tn.iteam.integration;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.mapper.ZabbixMonitoringMapper;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.snapshot.SnapshotStore;
import tn.iteam.monitoring.snapshot.StoredSnapshot;
import tn.iteam.service.MonitoredHostSnapshotService;
import tn.iteam.service.ServiceStatusPersistenceService;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.ZabbixMetricsService;
import tn.iteam.service.ZabbixProblemService;
import tn.iteam.service.ZabbixSyncService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ZabbixIntegrationService implements AsyncIntegrationService {

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
    private final MonitoredHostSnapshotService monitoredHostSnapshotService;
    private final ZabbixSyncService zabbixSyncService;

    @Override
    public MonitoringSourceType getSourceType() {
        return MonitoringSourceType.ZABBIX;
    }

    @Override
    public void refresh() {
        subscribeSafely("refresh", refreshAsync());
    }

    @Override
    public void refreshHosts() {
        String source = getSourceType().name();
        try {
            List<ServiceStatusDTO> statuses = List.copyOf(zabbixAdapter.fetchAll());
            serviceStatusPersistenceService.saveAll(statuses);
            zabbixSyncService.loadHostMap();
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
        subscribeSafely("refreshMetrics", refreshMetricsAsync());
    }

    public Mono<Void> refreshAsync() {
        return runLegacyStepAsync("refreshHosts", this::refreshHosts)
                .then(runLegacyStepAsync("refreshProblems", this::refreshProblems))
                .then(refreshMetricsAsync());
    }

    public Mono<Void> refreshMetricsAsync() {
        String source = getSourceType().name();
        return zabbixMetricsService.fetchAndSaveMetrics()
                .map(List::copyOf)
                .doOnNext(metrics -> saveSnapshot(
                        DATASET_METRICS,
                        source,
                        metrics.stream().map(monitoringMapper::toMetric).toList()
                ))
                .onErrorResume(throwable -> {
                    handleRefreshFailure(DATASET_METRICS, source, toException(throwable));
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> runLegacyStepAsync(String operation, Runnable action) {
        return Mono.fromRunnable(action)
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .onErrorResume(throwable -> {
                    log.warn("Zabbix {} async step failed but application remains available: {}", operation, safeMessage(throwable));
                    return Mono.<Void>empty();
                });
    }

    private void subscribeSafely(String operation, Mono<Void> pipeline) {
        pipeline.subscribe(
                unused -> {
                },
                throwable -> log.warn(
                        "Zabbix {} async failed but application remains available: {}",
                        operation,
                        safeMessage(throwable)
                )
        );
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
        List<?> existingSnapshot = safeGetExistingSnapshot(dataset, source).orElse(null);
        if (existingSnapshot != null) {
            saveFallbackSnapshot(dataset, source, existingSnapshot);
            availabilityService.markDegraded(source, safeMessage(exception));
            log.warn("Failed to refresh {} for {}. Keeping last snapshot: {}", dataset, source, safeMessage(exception));
            return;
        }

        List<?> persistedFallback = safeLoadPersistedFallback(dataset);
        if (!persistedFallback.isEmpty()) {
            saveFallbackSnapshot(dataset, source, persistedFallback);
            availabilityService.markDegraded(source, safeMessage(exception));
            log.warn(
                    "Failed to refresh {} for {}. Rebuilt snapshot from persisted data ({} entries): {}",
                    dataset,
                    source,
                    persistedFallback.size(),
                    safeMessage(exception)
            );
            return;
        }

        saveFallbackSnapshot(dataset, source, List.of());
        availabilityService.markUnavailable(source, safeMessage(exception));
        log.warn("Failed to refresh {} for {}. Serving empty snapshot fallback: {}", dataset, source, safeMessage(exception));
    }

    private List<?> loadPersistedFallback(String dataset) {
        return switch (dataset) {
            case DATASET_HOSTS -> monitoredHostSnapshotService.loadHosts(getSourceType());
            case DATASET_PROBLEMS -> zabbixProblemService.getPersistedFilteredActiveProblems()
                    .stream()
                    .map(monitoringMapper::toProblem)
                    .toList();
            case DATASET_METRICS -> zabbixMetricsService.getPersistedMetricsSnapshot()
                    .stream()
                    .map(monitoringMapper::toMetric)
                    .toList();
            default -> List.of();
        };
    }

    private Optional<List<?>> safeGetExistingSnapshot(String dataset, String source) {
        try {
            return snapshotStore.<List<?>>get(dataset, source)
                    .map(StoredSnapshot::data);
        } catch (Exception exception) {
            log.warn("Unable to read existing {} snapshot for {}: {}", dataset, source, safeMessage(exception));
            return Optional.empty();
        }
    }

    private List<?> safeLoadPersistedFallback(String dataset) {
        try {
            return loadPersistedFallback(dataset);
        } catch (Exception exception) {
            log.warn("Unable to load persisted {} fallback: {}", dataset, safeMessage(exception));
            return List.of();
        }
    }

    private void saveFallbackSnapshot(String dataset, String source, List<?> data) {
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

    private Exception toException(Throwable throwable) {
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(throwable);
    }
}
