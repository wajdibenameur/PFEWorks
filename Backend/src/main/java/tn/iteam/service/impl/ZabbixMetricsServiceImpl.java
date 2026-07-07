package tn.iteam.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.adapter.zabbix.ZabbixMetricsCollectionResult;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ZabbixMetricDTO;
import tn.iteam.exception.IntegrationException;
import tn.iteam.mapper.ZabbixMetricMapper;
import tn.iteam.repository.ZabbixMetricRepository;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.ZabbixDataQualityService;
import tn.iteam.service.ZabbixMetricsService;
import tn.iteam.service.ZabbixMetricsRefreshResult;
import tn.iteam.service.support.DatabasePersistenceGuard;
import tn.iteam.util.MonitoringConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ZabbixMetricsServiceImpl implements ZabbixMetricsService {

    private static final Logger log = LoggerFactory.getLogger(ZabbixMetricsServiceImpl.class);

    private static final String FETCHING_METRICS_MESSAGE = "Fetching Zabbix metrics";
    private static final String NO_METRICS_MESSAGE = "No Zabbix metrics to persist";
    private static final String EMPTY_METRIC_LOG_TEMPLATE = "Skipping invalid metric DTO: {}";
    private static final String SYNCHRONIZATION_FAILED_TEMPLATE = "{} metrics synchronization failed: {}";
    private static final String UNEXPECTED_ERROR_MESSAGE = "Unexpected error fetching Zabbix metrics";
    private static final String RECEIVED_METRICS_LOG_TEMPLATE = "Received {} metrics from Zabbix adapter";
    private static final String MAPPED_METRICS_LOG_TEMPLATE = "Mapped {} metrics, skipped {} invalid rows";
    private static final String PERSISTED_METRICS_LOG_TEMPLATE = "Persisted {} rows into zabbix_metric";
    private static final String METRICS_REFRESH_ALREADY_RUNNING_MESSAGE =
            "Zabbix heavy metrics refresh already running, skipping overlapping retrieval and reusing persisted snapshot";
    private static final String SKIPPED_CONCURRENT_REFRESH_LOG_TEMPLATE =
            "Skipped overlapping heavy metrics refresh for {}";
    private static final String REUSING_PERSISTED_METRICS_MESSAGE =
            "No live Zabbix metrics collected, reusing persisted snapshot";
    private static final int METRICS_PERSISTENCE_CHUNK_SIZE = 500;
    private static final int MAX_INVALID_DTO_LOGS = 20;

    private final AtomicBoolean metricsRefreshInProgress = new AtomicBoolean(false);
    @Value("${app.monitoring.zabbix.persisted-snapshot-limit:5000}")
    private int persistedSnapshotLimit;

    private final ZabbixAdapter adapter;
    private final ZabbixMetricMapper mapper;
    private final ZabbixMetricRepository repository;
    private final SourceAvailabilityService availabilityService;
    private final ZabbixDataQualityService dataQualityService;
    private final TransactionTemplate transactionTemplate;
    private final DatabasePersistenceGuard databasePersistenceGuard;

    @Override
    public List<ZabbixMetric> getPersistedMetricsSnapshot() {
        return loadLatestMetricsPerHostItem();
    }

    @Override
    public Mono<List<ZabbixMetric>> fetchAndSaveMetrics() {
        return fetchMetricsRefreshResult().map(ZabbixMetricsRefreshResult::metrics);
    }

    @Override
    public Mono<List<ZabbixMetric>> fetchAndSaveMetrics(JsonNode hosts) {
        return fetchMetricsRefreshResult(hosts).map(ZabbixMetricsRefreshResult::metrics);
    }

    @Override
    public Mono<ZabbixMetricsRefreshResult> fetchMetricsRefreshResult() {
        return fetchMetricsRefreshResult(null);
    }

    @Override
    public Mono<ZabbixMetricsRefreshResult> persistCollectedMetrics(ZabbixMetricsCollectionResult result) {
        if (!metricsRefreshInProgress.compareAndSet(false, true)) {
            log.warn(METRICS_REFRESH_ALREADY_RUNNING_MESSAGE);
            log.warn(SKIPPED_CONCURRENT_REFRESH_LOG_TEMPLATE, MonitoringConstants.SOURCE_ZABBIX);
            return Mono.fromCallable(this::getPersistedMetricsSnapshot)
                    .subscribeOn(Schedulers.boundedElastic())
                    .map(metrics -> new ZabbixMetricsRefreshResult(List.copyOf(metrics), false));
        }

        long startedAt = System.currentTimeMillis();
        return Mono.fromCallable(() -> doFetchMetricsRefresh(result))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(refreshResult -> {
                    if (refreshResult.partial()) {
                        availabilityService.markDegraded(
                                MonitoringConstants.SOURCE_ZABBIX,
                                "Partial Zabbix metrics collected"
                        );
                    } else {
                        availabilityService.markAvailable(MonitoringConstants.SOURCE_ZABBIX);
                    }
                    log.info("Zabbix metrics refresh finished durationMs={} partial={}",
                            System.currentTimeMillis() - startedAt, refreshResult.partial());
                })
                .onErrorResume(ex -> {
                    availabilityService.markUnavailable(
                            MonitoringConstants.SOURCE_ZABBIX,
                            ex.getMessage() != null ? ex.getMessage() : UNEXPECTED_ERROR_MESSAGE
                    );
                    if (ex instanceof IntegrationException integrationException) {
                        log.warn(
                                SYNCHRONIZATION_FAILED_TEMPLATE,
                                MonitoringConstants.SOURCE_LABEL_ZABBIX,
                                integrationException.getMessage()
                        );
                    } else {
                        log.error("{} [{}]", UNEXPECTED_ERROR_MESSAGE, MonitoringConstants.SOURCE_LABEL_ZABBIX, ex);
                    }
                    return Mono.error(ex);
                })
                .doFinally(signalType -> metricsRefreshInProgress.set(false));
    }

    @Override
    public Mono<ZabbixMetricsRefreshResult> fetchMetricsRefreshResult(JsonNode hosts) {
        log.info(FETCHING_METRICS_MESSAGE);
        return loadMetricCollection(hosts)
                .flatMap(this::persistCollectedMetrics)
                .doOnSuccess(result -> {
                });
    }

    private ZabbixMetricsRefreshResult doFetchMetricsRefresh(ZabbixMetricsCollectionResult result) {
        List<ZabbixMetricDTO> dtos = result.metrics();
        log.info(RECEIVED_METRICS_LOG_TEMPLATE, dtos.size());

        if (dtos.isEmpty()) {
            return new ZabbixMetricsRefreshResult(resolveEmptyMetricsResult(), false);
        }

        Map<String, ZabbixMetric> existingMetricsByKey = loadExistingMetricsByKey(dtos);
        MappingResult mappingResult = mapDtosToEntities(dtos, existingMetricsByKey);

        log.info(MAPPED_METRICS_LOG_TEMPLATE, mappingResult.entitiesToSave().size(), mappingResult.skippedInvalidRows());

        if (mappingResult.entitiesToSave().isEmpty()) {
            log.warn(NO_METRICS_MESSAGE);
            return new ZabbixMetricsRefreshResult(resolveEmptyMetricsResult(), result.partial());
        }

        AtomicReference<List<ZabbixMetric>> persistedBatchRef =
                new AtomicReference<>(List.copyOf(mappingResult.entitiesToSave()));
        boolean persisted = databasePersistenceGuard.safeRun(
                MonitoringConstants.SOURCE_ZABBIX,
                "metrics-persistence",
                () -> persistedBatchRef.set(persistMetricsInTransaction(mappingResult.entitiesToSave()))
        );
        List<ZabbixMetric> displaySnapshot = mergeFreshWithPersistedLatest(persistedBatchRef.get());
        boolean partial = result.partial() || !persisted;
        if (result.partial()) {
            log.warn("Partial Zabbix metrics collected, persisting valid rows and marking snapshot degraded");
        }
        return new ZabbixMetricsRefreshResult(displaySnapshot, partial);
    }

    private Mono<ZabbixMetricsCollectionResult> loadMetricCollection(JsonNode hosts) {
        return hosts == null
                ? adapter.fetchMetricsCollection()
                : adapter.fetchMetricsCollection(hosts);
    }

    private List<ZabbixMetric> resolveEmptyMetricsResult() {
        List<ZabbixMetric> persistedSnapshot = loadLatestMetricsPerHostItem();
        if (!persistedSnapshot.isEmpty()) {
            log.warn(REUSING_PERSISTED_METRICS_MESSAGE);
            return persistedSnapshot;
        }

        log.warn(NO_METRICS_MESSAGE);
        return List.of();
    }

    private Map<String, ZabbixMetric> loadExistingMetricsByKey(List<ZabbixMetricDTO> dtos) {
        List<ZabbixMetricDTO> validDtos = dtos.stream()
                .filter(this::isValidMetricDto)
                .toList();

        if (validDtos.isEmpty()) {
            return Map.of();
        }

        List<String> hostIds = validDtos.stream()
                .map(ZabbixMetricDTO::getHostId)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();

        List<String> itemIds = validDtos.stream()
                .map(ZabbixMetricDTO::getItemId)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();

        List<Long> timestamps = validDtos.stream()
                .map(ZabbixMetricDTO::getTimestamp)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();

        return repository.findAllByHostIdInAndItemIdInAndTimestampIn(hostIds, itemIds, timestamps)
                .stream()
                .collect(Collectors.toMap(this::buildKey, Function.identity(), (left, right) -> left));
    }

    private MappingResult mapDtosToEntities(List<ZabbixMetricDTO> dtos, Map<String, ZabbixMetric> existingMetricsByKey) {
        List<ZabbixMetric> entitiesToSave = new ArrayList<>();
        int skippedInvalidRows = 0;
        int skippedMissingHostId = 0;
        int skippedMissingItemId = 0;
        int skippedMissingTimestamp = 0;
        int skippedMissingMetricKey = 0;
        int skippedMissingValue = 0;
        int skippedMissingValueType = 0;
        int skippedMissingStatus = 0;
        int invalidLogged = 0;

        for (ZabbixMetricDTO dto : dtos) {
            String invalidReason = invalidMetricReason(dto);
            if (invalidReason != null) {
                log.debug(EMPTY_METRIC_LOG_TEMPLATE, dto);
                skippedInvalidRows++;
                switch (invalidReason) {
                    case "hostId missing" -> skippedMissingHostId++;
                    case "itemId missing" -> skippedMissingItemId++;
                    case "timestamp missing" -> skippedMissingTimestamp++;
                    case "metricKey missing" -> skippedMissingMetricKey++;
                    case "value missing" -> skippedMissingValue++;
                    case "valueType missing" -> skippedMissingValueType++;
                    case "status missing" -> skippedMissingStatus++;
                    default -> {
                    }
                }
                if (invalidLogged < MAX_INVALID_DTO_LOGS) {
                    invalidLogged++;
                    log.info(
                            "Zabbix DTO rejected {}: hostId={}, itemId={}, metricKey={}, value={}, valueType={}, status={}, timestamp={}, reason={}",
                            invalidLogged,
                            dto != null ? dto.getHostId() : null,
                            dto != null ? dto.getItemId() : null,
                            dto != null ? dto.getMetricKey() : null,
                            dto != null ? dto.getValue() : null,
                            dto != null ? dto.getValueType() : null,
                            dto != null ? dto.getStatus() : null,
                            dto != null ? dto.getTimestamp() : null,
                            invalidReason
                    );
                }
                continue;
            }

            ZabbixMetric incoming = mapper.toEntity(dto);
            String key = buildKey(dto.getHostId(), dto.getItemId(), dto.getTimestamp());
            ZabbixMetric existing = existingMetricsByKey.get(key);

            entitiesToSave.add(existing != null ? mergeMetric(existing, incoming) : incoming);
        }

        if (skippedInvalidRows > 0) {
            log.info(
                    "Zabbix DTO mapping summary: totalDtos={}, mappedDtos={}, skippedInvalidDtos={}, skippedMissingHostId={}, skippedMissingItemId={}, skippedMissingTimestamp={}, skippedMissingMetricKey={}, skippedMissingValue={}, skippedMissingValueType={}, skippedMissingStatus={}",
                    dtos.size(),
                    entitiesToSave.size(),
                    skippedInvalidRows,
                    skippedMissingHostId,
                    skippedMissingItemId,
                    skippedMissingTimestamp,
                    skippedMissingMetricKey,
                    skippedMissingValue,
                    skippedMissingValueType,
                    skippedMissingStatus
            );
        }

        return new MappingResult(entitiesToSave, skippedInvalidRows);
    }

    private boolean isValidMetricDto(ZabbixMetricDTO dto) {
        return invalidMetricReason(dto) == null;
    }

    private String invalidMetricReason(ZabbixMetricDTO dto) {
        if (dto == null) {
            return "dto is null";
        }
        if (dto.getHostId() == null || dto.getHostId().isBlank()) {
            return "hostId missing";
        }
        if (dto.getItemId() == null || dto.getItemId().isBlank()) {
            return "itemId missing";
        }
        if (dto.getTimestamp() == null || dto.getTimestamp() <= 0) {
            return "timestamp missing";
        }
        if (dto.getMetricKey() == null || dto.getMetricKey().isBlank()) {
            return "metricKey missing";
        }
        if (dto.getValue() == null) {
            return "value missing";
        }
        if (dto.getValueType() == null) {
            return "valueType missing";
        }
        if (dto.getStatus() == null || dto.getStatus().isBlank()) {
            return "status missing";
        }
        return dto.getHostId() != null && !dto.getHostId().isBlank()
                ? null
                : "unknown invalid DTO";
    }

    private String buildKey(ZabbixMetric metric) {
        return buildKey(metric.getHostId(), metric.getItemId(), metric.getTimestamp());
    }

    private String buildKey(String hostId, String itemId, Long timestamp) {
        return hostId + "|" + itemId + "|" + timestamp;
    }

    private ZabbixMetric mergeMetric(ZabbixMetric existing, ZabbixMetric incoming) {
        existing.setHostName(incoming.getHostName());
        existing.setMetricName(incoming.getMetricName());
        existing.setMetricKey(incoming.getMetricKey());
        existing.setSource(incoming.getSource());
        existing.setValueType(incoming.getValueType());
        existing.setStatus(incoming.getStatus());
        existing.setUnits(incoming.getUnits());
        existing.setValue(incoming.getValue());
        existing.setTimestamp(incoming.getTimestamp());
        existing.setIp(incoming.getIp());
        existing.setPort(incoming.getPort());
        return existing;
    }

    private List<ZabbixMetric> persistMetricsInTransaction(List<ZabbixMetric> entitiesToSave) {
        return transactionTemplate.execute(status -> {
            long startedAt = System.currentTimeMillis();
            List<ZabbixMetric> saved = new ArrayList<>(entitiesToSave.size());
            for (int index = 0; index < entitiesToSave.size(); index += METRICS_PERSISTENCE_CHUNK_SIZE) {
                int end = Math.min(index + METRICS_PERSISTENCE_CHUNK_SIZE, entitiesToSave.size());
                List<ZabbixMetric> chunk = entitiesToSave.subList(index, end);
                saved.addAll(repository.saveAll(chunk));
                repository.flush();
            }

            dataQualityService.logMetricQualitySummary(saved);
            log.info(PERSISTED_METRICS_LOG_TEMPLATE, saved.size());
            log.info("Saved/Updated {} Zabbix metrics in {} ms", saved.size(), System.currentTimeMillis() - startedAt);

            return saved;
        });
    }

    private List<ZabbixMetric> mergeFreshWithPersistedLatest(List<ZabbixMetric> freshMetrics) {
        Map<String, ZabbixMetric> byHostAndItem = loadLatestMetricsPerHostItem().stream()
                .collect(Collectors.toMap(
                        metric -> metric.getHostId() + "|" + metric.getItemId(),
                        Function.identity(),
                        this::pickLatestMetric
                ));

        for (ZabbixMetric metric : freshMetrics) {
            if (metric.getHostId() == null || metric.getHostId().isBlank()
                    || metric.getItemId() == null || metric.getItemId().isBlank()) {
                continue;
            }
            String key = metric.getHostId() + "|" + metric.getItemId();
            ZabbixMetric existing = byHostAndItem.get(key);
            byHostAndItem.put(key, existing == null ? metric : pickLatestMetric(existing, metric));
        }

        return byHostAndItem.values().stream()
                .sorted(Comparator.comparing(ZabbixMetric::getTimestamp, Comparator.nullsLast(Long::compareTo)).reversed())
                .limit(resolveSnapshotLimit())
                .toList();
    }

    private ZabbixMetric pickLatestMetric(ZabbixMetric left, ZabbixMetric right) {
        long leftTs = left.getTimestamp() != null ? left.getTimestamp() : Long.MIN_VALUE;
        long rightTs = right.getTimestamp() != null ? right.getTimestamp() : Long.MIN_VALUE;
        return rightTs >= leftTs ? right : left;
    }

    private List<ZabbixMetric> loadLatestMetricsPerHostItem() {
        return repository.findLatestByHostAndItem().stream()
                .sorted(Comparator.comparing(ZabbixMetric::getTimestamp, Comparator.nullsLast(Long::compareTo)).reversed())
                .limit(resolveSnapshotLimit())
                .toList();
    }

    private int resolveSnapshotLimit() {
        return persistedSnapshotLimit > 0 ? persistedSnapshotLimit : 5000;
    }

    private record MappingResult(List<ZabbixMetric> entitiesToSave, int skippedInvalidRows) {
    }
}
