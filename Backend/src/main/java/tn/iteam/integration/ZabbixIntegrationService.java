package tn.iteam.integration;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.adapter.zabbix.ZabbixHostStatusEnrichmentSummary;
import tn.iteam.adapter.zabbix.ZabbixMetricsCollectionResult;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.mapper.ZabbixMonitoringMapper;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.snapshot.SnapshotStore;
import tn.iteam.monitoring.snapshot.StoredSnapshot;
import tn.iteam.service.*;
import tn.iteam.util.MonitoringConstants;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ZabbixIntegrationService implements AsyncIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(ZabbixIntegrationService.class);
    private static final long HOSTS_SNAPSHOT_TTL_MS = 60_000L;
    private static final String DATASET_HOSTS = "hosts";
    private static final String DATASET_METRICS = "metrics";
    private static final String DATASET_PROBLEMS = "problems";
    private static final String FRESHNESS_LIVE = StoredSnapshot.FRESHNESS_LIVE;
    private static final String FRESHNESS_MEMORY_SNAPSHOT = StoredSnapshot.FRESHNESS_MEMORY_SNAPSHOT_FALLBACK;
    private static final String FRESHNESS_DATABASE_SNAPSHOT = StoredSnapshot.FRESHNESS_DATABASE_SNAPSHOT_FALLBACK;
    private static final String FRESHNESS_SNAPSHOT_MISSING = StoredSnapshot.FRESHNESS_SNAPSHOT_MISSING;

    private final ZabbixAdapter zabbixAdapter;
    private final ZabbixMonitoringMapper monitoringMapper;
    private final ServiceStatusPersistenceService serviceStatusPersistenceService;
    private final ZabbixProblemService zabbixProblemService;
    private final ZabbixMetricsService zabbixMetricsService;
    private final SnapshotStore snapshotStore;
    private final SourceAvailabilityService availabilityService;
    private final MonitoredHostSnapshotService monitoredHostSnapshotService;
    private final ZabbixHostSyncService zabbixSyncService;
    private final Object hostsSnapshotLock = new Object();
    private volatile JsonNode lastHostsSnapshot;
    private volatile long lastHostsSnapshotAt;

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
        JsonNode hosts = zabbixAdapter.fetchHosts();
        updateHostsSnapshot(hosts, "live-refreshHosts");
        refreshHosts(hosts);
    }

    private void refreshHosts(JsonNode hosts) {
        String source = getSourceType().name();
        try {
            // Step 1: Fetch live data
            List<ServiceStatusDTO> statuses = List.copyOf(hasHosts(hosts)
                    ? zabbixAdapter.fetchAll(hosts)
                    : zabbixAdapter.fetchAll());
            
            // Step 2: Save snapshot FIRST (always succeeds, in-memory)
            zabbixSyncService.loadHostMap(hosts);
            List<UnifiedMonitoringHostDTO> persistedHostsSnapshot = monitoredHostSnapshotService.loadHosts(getSourceType());
            long persistedHostsWithIds = persistedHostsSnapshot.stream()
                    .filter(host -> host.getHostId() != null && !host.getHostId().isBlank())
                    .count();
            log.debug("Zabbix persisted hosts snapshot after refreshHosts contains {} hosts ({} with non-blank hostId)",
                    persistedHostsSnapshot.size(), persistedHostsWithIds);
            saveSnapshot(
                    DATASET_HOSTS,
                    source,
                    persistedHostsSnapshot
            );
            
            // Step 3: Try DB persistence (non-blocking, wrapped)
            tryPersistToDatabase(() -> serviceStatusPersistenceService.saveAll(statuses));
        } catch (Exception exception) {
            handleRefreshFailure(DATASET_HOSTS, source, exception);
        }
    }

    @Override
    public void refreshProblems() {
        refreshProblems(resolveHostsSnapshot());
    }

    private void refreshProblems(JsonNode hosts) {
        String source = getSourceType().name();
        try {
            // Step 1: Fetch live data
            List<ZabbixProblemDTO> problems = List.copyOf(hasHosts(hosts)
                    ? zabbixProblemService.synchronizeActiveProblemsFromZabbix(hosts)
                    : zabbixProblemService.synchronizeActiveProblemsFromZabbix());
            
            // Step 2: Save snapshot FIRST (always succeeds, in-memory)
            saveSnapshot(
                    DATASET_PROBLEMS,
                    source,
                    problems.stream().map(monitoringMapper::toProblem).toList()
            );
            
            // Step 3: Try DB persistence (non-blocking, wrapped) - skipped for problems
            // Problems are handled by ZabbixProblemService internally
        } catch (Exception exception) {
            handleRefreshFailure(DATASET_PROBLEMS, source, exception);
        }
    }

    private void refreshProblems(List<ZabbixProblemDTO> problems) {
        String source = getSourceType().name();
        try {
            List<ZabbixProblemDTO> synchronizedProblems = List.copyOf(zabbixProblemService.synchronizeActiveProblems(problems));
            saveSnapshot(
                    DATASET_PROBLEMS,
                    source,
                    synchronizedProblems.stream().map(monitoringMapper::toProblem).toList()
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
        String source = getSourceType().name();
        return Mono.fromCallable(zabbixAdapter::fetchHosts)
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(hosts -> updateHostsSnapshot(hosts, "live-refreshAsync"))
            .flatMap(hosts -> Mono.zip(
                collectProblemsForCycleAsync(hosts),
                collectMetricsForCycle(hosts)
            ).flatMap(tuple -> {
                List<ZabbixProblemDTO> problems = tuple.getT1();
                ZabbixMetricsCollectionResult metricsCollection = tuple.getT2();
                return runLegacyStepAsync("refreshHosts", () ->
                        refreshHosts(hosts, buildEnrichedHostStatuses(hosts, problems, metricsCollection)))
                    .then(runLegacyStepAsync("refreshProblems", () -> refreshProblems(problems)))
                    .then(refreshCollectedMetricsAsync(metricsCollection));
            }))
            .onErrorResume(throwable -> {
                handleRefreshFailure(DATASET_HOSTS, source, toException(throwable));
                return Mono.empty();
            });
    }

    private Mono<List<ZabbixProblemDTO>> collectProblemsForCycleAsync(JsonNode hosts) {
        return Mono.fromCallable(() -> collectProblemsForCycle(hosts))
            .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> refreshMetricsAsync() {
        return refreshMetricsAsync(null);
    }

    private Mono<Void> refreshMetricsAsync(JsonNode hosts) {
        String source = getSourceType().name();
        JsonNode resolvedHosts = hasHosts(hosts) ? hosts : resolveHostsSnapshot();
        Mono<List<ZabbixMetric>> metricsMono = !hasHosts(resolvedHosts)
                ? zabbixMetricsService.fetchMetricsRefreshResult()
                        .doOnNext(result -> handleMetricsRefreshResult(source, result))
                        .map(ZabbixMetricsRefreshResult::metrics)
                : zabbixMetricsService.fetchMetricsRefreshResult(resolvedHosts)
                        .doOnNext(result -> handleMetricsRefreshResult(source, result))
                        .map(ZabbixMetricsRefreshResult::metrics);

        return metricsMono
                .map(List::copyOf)
                .onErrorResume(throwable -> {
                    handleRefreshFailure(DATASET_METRICS, source, toException(throwable));
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> refreshCollectedMetricsAsync(ZabbixMetricsCollectionResult metricsCollection) {
        String source = getSourceType().name();
        return zabbixMetricsService.persistCollectedMetrics(metricsCollection)
                .doOnNext(result -> handleMetricsRefreshResult(source, result))
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

    private void handleMetricsRefreshResult(String source, ZabbixMetricsRefreshResult result) {
        saveSnapshot(
                DATASET_METRICS,
                source,
                result.metrics().stream().map(monitoringMapper::toMetric).toList(),
                result.partial(),
                FRESHNESS_LIVE
        );

        if (result.partial()) {
            availabilityService.markDegraded(source, "Partial Zabbix metrics collected");
            log.warn("Partial Zabbix metrics collected");
        }
    }

    private List<ZabbixProblemDTO> collectProblemsForCycle(JsonNode hosts) {
        String source = getSourceType().name();
        try {
            List<ZabbixProblemDTO> problems = List.copyOf(zabbixAdapter.fetchProblems(hosts));
            return List.copyOf(zabbixProblemService.synchronizeActiveProblems(problems));
        } catch (Exception exception) {
            handleRefreshFailure(DATASET_PROBLEMS, source, exception);
            return List.of();
        }
    }

    private Mono<ZabbixMetricsCollectionResult> collectMetricsForCycle(JsonNode hosts) {
        String source = getSourceType().name();
        return zabbixAdapter.fetchMetricsCollection(hosts)
                .onErrorResume(throwable -> {
                    handleRefreshFailure(DATASET_METRICS, source, toException(throwable));
                    return Mono.just(new ZabbixMetricsCollectionResult(List.of(), false));
                });
    }

    private List<ServiceStatusDTO> buildEnrichedHostStatuses(
            JsonNode hosts,
            List<ZabbixProblemDTO> problems,
            ZabbixMetricsCollectionResult metricsCollection
    ) {
        List<ServiceStatusDTO> statuses = List.copyOf(zabbixAdapter.mapHostsToDto(hosts));
        ZabbixHostStatusEnrichmentSummary summary = zabbixAdapter.enrichRealStatuses(
                statuses,
                problems,
                metricsCollection.metrics(),
                metricsCollection.partial()
        );
        log.debug("Zabbix host enrichment summary: hosts={}, downByPing={}, degradedByProblems={}, degradedByPartialMetrics={}",
                summary.hostsEnriched(),
                summary.downByPing(),
                summary.degradedByProblems(),
                summary.degradedByPartialMetrics());
        return statuses;
    }

    private void refreshHosts(JsonNode hosts, List<ServiceStatusDTO> statuses) {
        String source = getSourceType().name();
        try {
            zabbixSyncService.loadHostMap(hosts);
            List<UnifiedMonitoringHostDTO> persistedHostsSnapshot = monitoredHostSnapshotService.loadHosts(getSourceType());
            long persistedHostsWithIds = persistedHostsSnapshot.stream()
                    .filter(host -> host.getHostId() != null && !host.getHostId().isBlank())
                    .count();
            log.debug("Zabbix persisted hosts snapshot after refreshHosts contains {} hosts ({} with non-blank hostId)",
                    persistedHostsSnapshot.size(), persistedHostsWithIds);
            saveSnapshot(
                    DATASET_HOSTS,
                    source,
                    persistedHostsSnapshot
            );
            tryPersistToDatabase(() -> serviceStatusPersistenceService.saveAll(statuses));
        } catch (Exception exception) {
            handleRefreshFailure(DATASET_HOSTS, source, exception);
        }
    }

    private <T> void saveSnapshot(String dataset, String source, List<T> data) {
        saveSnapshot(dataset, source, data, false, FRESHNESS_LIVE);
    }

    private <T> void saveSnapshot(String dataset, String source, List<T> data, boolean degraded, String freshnessValue) {
        try {
            snapshotStore.save(
                    dataset,
                    source,
                    StoredSnapshot.of(data, degraded, Map.of(source, freshnessValue))
            );
            if (!degraded) {
                availabilityService.markAvailable(source);
            }
            log.debug("Stored {} {} snapshot entries for {}", data.size(), dataset, source);
        } catch (Exception exception) {
            log.warn("Unable to store {} snapshot for {}: {}", dataset, source, safeMessage(exception));
        }
    }

    private void handleRefreshFailure(String dataset, String source, Exception exception) {
        snapshotStore.<List<?>>get(dataset, source)
                .ifPresentOrElse(existing -> {
                    hydrateHostsSnapshotFromData(dataset, existing.data(), "memory-snapshot");
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
                        hydrateHostsSnapshotFromData(dataset, persistedFallback, "database-fallback");
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
                    new StoredSnapshot<>(data, true, Map.of(source, FRESHNESS_MEMORY_SNAPSHOT), Instant.now())
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

    private void tryPersistToDatabase(Runnable persistenceAction) {
        try {
            persistenceAction.run();
        } catch (Exception ex) {
            log.warn("Database unavailable, skipping persistence: {}", ex.getMessage());
        }
    }

    private Exception toException(Throwable throwable) {
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(throwable);
    }

    private boolean hasHosts(JsonNode hosts) {
        return hosts != null && hosts.isArray();
    }

    private JsonNode resolveHostsSnapshot() {
        JsonNode cachedHosts = lastHostsSnapshot;
        long cachedAt = lastHostsSnapshotAt;
        long ageMs = System.currentTimeMillis() - cachedAt;

        if (hasHosts(cachedHosts) && cachedAt > 0 && ageMs >= 0 && ageMs <= HOSTS_SNAPSHOT_TTL_MS) {
            log.debug("Reusing cached Zabbix hosts snapshot (age={} ms)", ageMs);
            return cachedHosts;
        }

        synchronized (hostsSnapshotLock) {
            cachedHosts = lastHostsSnapshot;
            cachedAt = lastHostsSnapshotAt;
            ageMs = System.currentTimeMillis() - cachedAt;

            if (hasHosts(cachedHosts) && cachedAt > 0 && ageMs >= 0 && ageMs <= HOSTS_SNAPSHOT_TTL_MS) {
                log.debug("Reusing cached Zabbix hosts snapshot after lock (age={} ms)", ageMs);
                return cachedHosts;
            }

            if (!hasHosts(cachedHosts) || cachedAt <= 0) {
                log.debug("No cached Zabbix hosts snapshot available, trying persisted hosts fallback before live fetch");
            } else {
                log.debug("Cached Zabbix hosts snapshot expired (age={} ms, ttl={} ms), trying persisted hosts fallback before live fetch",
                        ageMs, HOSTS_SNAPSHOT_TTL_MS);
            }

            logExistingHostsMemoryFallback();
            if (tryHydrateHostsSnapshotFromPersistedFallback("resolve-persisted-fallback")) {
                JsonNode hydratedHosts = lastHostsSnapshot;
                long hydratedAgeMs = System.currentTimeMillis() - lastHostsSnapshotAt;
                log.debug("Reusing hydrated Zabbix hosts snapshot from persisted fallback (age={} ms)", hydratedAgeMs);
                return hydratedHosts;
            }

            log.debug("No usable persisted Zabbix hosts fallback found, fetching live hosts");
            JsonNode liveHosts = zabbixAdapter.fetchHosts();
            updateHostsSnapshot(liveHosts, "live-resolveHostsSnapshot");
            return liveHosts;
        }
    }

    private void updateHostsSnapshot(JsonNode hosts, String origin) {
        if (!hasHosts(hosts)) {
            log.debug("Skipping Zabbix hosts snapshot update from {} because payload is empty or invalid", origin);
            return;
        }
        lastHostsSnapshot = hosts;
        lastHostsSnapshotAt = System.currentTimeMillis();
        log.debug("Updated Zabbix hosts snapshot from {} with {} hosts", origin, hosts.size());
    }

    private void hydrateHostsSnapshotFromData(String dataset, List<?> data, String origin) {
        if (!DATASET_HOSTS.equals(dataset) || data == null || data.isEmpty()) {
            log.debug("Skipping hosts snapshot hydration from {} because dataset={} or data is empty", origin, dataset);
            return;
        }

        log.debug("Attempting Zabbix hosts snapshot hydration from {} with {} entries", origin, data.size());
        ArrayNode hosts = JsonNodeFactory.instance.arrayNode();
        int validHosts = 0;
        for (Object row : data) {
            if (!(row instanceof UnifiedMonitoringHostDTO host)) {
                log.warn("Skipping non-UnifiedMonitoringHostDTO entry while hydrating Zabbix hosts snapshot from {}: {}",
                        origin, row != null ? row.getClass().getName() : "null");
                continue;
            }

            String hostId = host.getHostId();
            if (hostId == null || hostId.isBlank()) {
                log.warn("Skipping persisted Zabbix host without hostId while hydrating from {}: name={}, ip={}",
                        origin, host.getName(), host.getIp());
                continue;
            }

            ObjectNode hostNode = hosts.addObject();
            hostNode.put(MonitoringConstants.HOST_ID_FIELD, hostId);
            hostNode.put(MonitoringConstants.HOST_FIELD,
                    host.getName() != null && !host.getName().isBlank() ? host.getName() : hostId);
            hostNode.put(MonitoringConstants.STATUS_FIELD,
                    MonitoringConstants.STATUS_UP.equalsIgnoreCase(host.getStatus()) ? 0 : 1);

            ArrayNode interfaces = hostNode.putArray("interfaces");
            ObjectNode iface = interfaces.addObject();
            iface.put(MonitoringConstants.MAIN_FIELD, 1);
            iface.put(MonitoringConstants.IP_FIELD,
                    host.getIp() != null && !host.getIp().isBlank() ? host.getIp() : MonitoringConstants.IP_UNKNOWN);
            iface.put(MonitoringConstants.PORT_FIELD, host.getPort() != null ? host.getPort() : 10050);
            validHosts++;
        }

        if (hosts.isEmpty()) {
            log.warn("Unable to hydrate Zabbix hosts snapshot from {} because no valid persisted hosts were found in {} entries",
                    origin, data.size());
            return;
        }

        log.debug("Hydrated Zabbix hosts snapshot from {} with {} valid hosts out of {} entries",
                origin, validHosts, data.size());
        updateHostsSnapshot(hosts, origin);
    }

    private boolean tryHydrateHostsSnapshotFromPersistedFallback(String origin) {
        try {
            List<UnifiedMonitoringHostDTO> persistedHosts = monitoredHostSnapshotService.loadHosts(getSourceType());
            if (persistedHosts == null || persistedHosts.isEmpty()) {
                log.warn("No persisted Zabbix hosts available in DB fallback for {}", origin);
                return false;
            }

            long persistedHostsWithIds = persistedHosts.stream()
                    .filter(host -> host.getHostId() != null && !host.getHostId().isBlank())
                    .count();
            log.debug("Found {} persisted Zabbix hosts in DB fallback for {} ({} with non-blank hostId)",
                    persistedHosts.size(), origin, persistedHostsWithIds);
            hydrateHostsSnapshotFromData(DATASET_HOSTS, List.copyOf(persistedHosts), origin);
            return hasHosts(lastHostsSnapshot);
        } catch (Exception exception) {
            log.warn("Unable to hydrate Zabbix hosts snapshot from persisted fallback {}: {}", origin, safeMessage(exception));
            return false;
        }
    }

    private void logExistingHostsMemoryFallback() {
        try {
            Optional<StoredSnapshot<List<?>>> snapshot = snapshotStore.get(DATASET_HOSTS, getSourceType().name());
            if (snapshot.isEmpty()) {
                log.warn("No in-memory Zabbix hosts snapshot found in SnapshotStore for fallback");
                return;
            }

            List<?> data = snapshot.get().data();
            int size = data != null ? data.size() : 0;
            log.debug("Found in-memory Zabbix hosts snapshot fallback with {} entries and freshness={}",
                    size, snapshot.get().freshness().get(getSourceType().name()));
        } catch (Exception exception) {
            log.warn("Unable to inspect in-memory Zabbix hosts snapshot fallback: {}", safeMessage(exception));
        }
    }
}
