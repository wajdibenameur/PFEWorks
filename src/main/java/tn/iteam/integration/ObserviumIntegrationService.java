package tn.iteam.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
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
import tn.iteam.repository.ObserviumMetricRepository;
import tn.iteam.repository.ObserviumProblemRepository;
import tn.iteam.service.MonitoredHostPersistenceService;
import tn.iteam.service.MonitoredHostSnapshotService;
import tn.iteam.service.ObserviumPersistenceService;
import tn.iteam.service.ServiceStatusPersistenceService;
import tn.iteam.service.SourceAvailabilityService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ObserviumIntegrationService implements AsyncIntegrationService {

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
    private final MonitoredHostPersistenceService monitoredHostPersistenceService;
    private final MonitoredHostSnapshotService monitoredHostSnapshotService;
    private final ObserviumProblemRepository observiumProblemRepository;
    private final ObserviumMetricRepository observiumMetricRepository;

    @Override
    public MonitoringSourceType getSourceType() {
        return MonitoringSourceType.OBSERVIUM;
    }

    @Override
    public void refresh() {
        subscribeSafely("refresh", refreshAsync());
    }

    @Override
    public void refreshHosts() {
        String source = getSourceType().name();
        try {
            List<ServiceStatusDTO> statuses = List.copyOf(observiumAdapter.fetchAll());
            serviceStatusPersistenceService.saveAll(statuses);
            monitoredHostPersistenceService.saveAll(source, statuses);
            List<UnifiedMonitoringHostDTO> data = monitoredHostSnapshotService.loadHosts(getSourceType());
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
                    List<ObserviumMetricDTO> metrics = List.copyOf(observiumAdapter.fetchMetrics());
                    observiumPersistenceService.saveMetrics(metrics);
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
            case DATASET_PROBLEMS -> observiumProblemRepository.findByActiveTrue().stream()
                    .map(problem -> monitoringMapper.toProblem(ObserviumProblemDTO.builder()
                            .problemId(problem.getProblemId())
                            .hostId(problem.getHostId())
                            .host(problem.getDevice())
                            .description(problem.getDescription())
                            .severity(problem.getSeverity())
                            .active(Boolean.TRUE.equals(problem.getActive()))
                            .eventId(problem.getEventId())
                            .startedAt(problem.getStartedAt())
                            .resolvedAt(problem.getResolvedAt())
                            .build()))
                    .toList();
            case DATASET_METRICS -> observiumMetricRepository.findAll().stream()
                    .map(metric -> monitoringMapper.toMetric(ObserviumMetricDTO.builder()
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

    private Mono<Void> runStepAsync(String operation, Runnable action) {
        return Mono.fromRunnable(action)
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .onErrorResume(throwable -> {
                    log.warn("Observium {} async step failed but application remains available: {}", operation, safeMessage(throwable));
                    return Mono.<Void>empty();
                });
    }

    private void subscribeSafely(String operation, Mono<Void> pipeline) {
        pipeline.subscribe(
                unused -> {
                },
                throwable -> log.warn("Observium {} async failed but application remains available: {}", operation, safeMessage(throwable))
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
