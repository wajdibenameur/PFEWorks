package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tn.iteam.domain.MonitoredHost;
import tn.iteam.dto.ZabbixMetricDTO;
import tn.iteam.service.ZabbixHostSyncService;
import tn.iteam.util.IntegrationClientSupport;
import tn.iteam.util.MonitoringConstants;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collector dedicated to Zabbix metrics and history collection.
 * Handles items retrieval, history batch processing, and metric mapping.
 */
@Component
public class ZabbixMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(ZabbixMetricsCollector.class);
    private static final String LOG_PREFIX = "[ZABBIX-METRICS-COLLECTOR] ";
    private static final int HISTORY_BATCH_SIZE = 50;
    private static final long HISTORY_FALLBACK_WINDOW_SECONDS = 3600;
    private static final int DEFAULT_HOST_ITEMS_BATCH_SIZE = 2;

    private static final String KEY_FIELD = "key_";
    private static final String ITEM_ID_FIELD = "itemid";
    private static final String VALUE_TYPE_FIELD = "value_type";
    private static final String LAST_VALUE_FIELD = "lastvalue";
    private static final String LAST_CLOCK_FIELD = "lastclock";
    private static final String STATUS_FIELD = "status";
    private static final String STATE_FIELD = "state";
    private static final String UNITS_FIELD = "units";

    private static final String FETCHING_ITEMS_BATCH_LOG_TEMPLATE = LOG_PREFIX + "Fetching Zabbix metrics batch {}/{} with {} hosts";
    private static final String ITEMS_BATCH_DURATION_LOG_TEMPLATE = LOG_PREFIX + "Finished metrics batch {}/{} in {} ms";
    private static final String MAPPED_ITEMS_BATCH_LOG_TEMPLATE = LOG_PREFIX + "Mapped {} metrics from current batch";
    private static final String TIMED_OUT_ITEMS_BATCH_LOG_TEMPLATE = LOG_PREFIX + "Batch timeout for hosts {} after {} ms";
    private static final String FAILED_ITEMS_BATCH_LOG_TEMPLATE = LOG_PREFIX + "Batch failed for hosts {} after {} ms";
    private static final String NO_SUCCESSFUL_ITEMS_BATCHES_LOG = LOG_PREFIX + "No Zabbix metrics batch completed successfully";
    private static final String FALLBACK_HISTORY_LOG_TEMPLATE = LOG_PREFIX + "Loading fallback history for {} items";
    private static final String RECEIVED_ITEMS_LOG_TEMPLATE = LOG_PREFIX + "Received {} items from Zabbix API";
    private static final String RECEIVED_ITEMS_BATCH_LOG_TEMPLATE = LOG_PREFIX + "Received {} items from Zabbix API for host batch size={}";
    private static final String FETCHED_ITEMS_LOG_TEMPLATE = LOG_PREFIX + "Useful items fetched: float={}, int={}";
    private static final String METRIC_COUNT_LOG_TEMPLATE = LOG_PREFIX + "Final mapped metrics count={}";
    private static final String FLOAT_BATCH_ERROR_TEMPLATE = LOG_PREFIX + "Failed float history batch size={}, skipping batch";
    private static final String INT_BATCH_ERROR_TEMPLATE = LOG_PREFIX + "Failed int history batch size={}, skipping batch";
    private static final String FULL_FETCH_START_LOG = LOG_PREFIX + "FULL FETCH START metrics";
    private static final String INCREMENTAL_FETCH_START_LOG = LOG_PREFIX + "INCREMENTAL FETCH START metrics";
    private static final String LASTCLOCK_USED_LOG = LOG_PREFIX + "LASTCLOCK USED metrics={}";
    private static final String ITEMS_SKIPPED_OLD_LOG = LOG_PREFIX + "ITEMS SKIPPED OLD LASTCLOCK={}";
    private static final String NEW_ITEMS_DETECTED_LOG = LOG_PREFIX + "NEW ITEMS DETECTED={}";
    private static final String DELTA_FETCH_DONE_LOG = LOG_PREFIX + "DELTA FETCH DONE durationMs={} fetched={} skippedOld={} deltaRatio={} payloadApproxBytes={}";
    private static final String FALLBACK_FULL_RESYNC_LOG = LOG_PREFIX + "FALLBACK FULL RESYNC metrics reason={}";
    private static final String NO_CHANGES_SKIP_LOG = LOG_PREFIX + "NO CHANGES DETECTED latestClock={} lastSuccessfulClock={} -> SKIP heavy metrics collection";

    private static final String UNKNOWN_SOURCE_LABEL = MonitoringConstants.SOURCE_LABEL_ZABBIX;

    private final ZabbixClient zabbixClient;

    private final ZabbixHostSyncService syncService;
    private final ZabbixSyncStateService syncStateService;
    @Value("${zabbix.metrics.host-batch-size:2}")
    private int configuredHostItemsBatchSize;

    public ZabbixMetricsCollector(ZabbixClient zabbixClient, ZabbixHostSyncService syncService, ZabbixSyncStateService syncStateService) {
        this.zabbixClient = zabbixClient;
        this.syncService = syncService;
        this.syncStateService = syncStateService;
    }

    /**
     * Fetch all metrics from Zabbix using current hosts.
     *
     * @return Mono of list of ZabbixMetricDTO
     */
    public Mono<List<ZabbixMetricDTO>> fetchMetricsAndMap() {
        return fetchMetricsCollection().map(ZabbixMetricsCollectionResult::metrics);
    }

    /**
     * Fetch metrics with pre-resolved hosts.
     *
     * @param hosts JsonNode containing hosts array
     * @return Mono of list of ZabbixMetricDTO
     */
    public Mono<List<ZabbixMetricDTO>> fetchMetricsAndMap(JsonNode hosts) {
        return fetchMetricsCollection(hosts).map(ZabbixMetricsCollectionResult::metrics);
    }

    public Mono<ZabbixMetricsCollectionResult> fetchMetricsCollection() {
        return zabbixClient.getHosts().flatMap(this::fetchMetricsCollection);
    }

    public Mono<ZabbixMetricsCollectionResult> fetchMetricsCollection(JsonNode hosts) {
        if (hosts == null || !hosts.isArray()) {
            return Mono.just(new ZabbixMetricsCollectionResult(List.of(), false));
        }

        return Mono.fromCallable(() -> syncService.loadHostMap(hosts))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(hostMap -> processMetrics(hosts, hostMap));
    }

    private Mono<ZabbixMetricsCollectionResult> processMetrics(JsonNode hosts, Map<String, MonitoredHost> hostMap) {
        Map<String, ZabbixMetricDTO> metricsByKey = new LinkedHashMap<>();
        List<String> hostIds = new ArrayList<>();
        Map<String, String> hostNames = new HashMap<>();

        for (JsonNode host : hosts) {
            String id = host.path(MonitoringConstants.HOST_ID_FIELD).asText();
            hostIds.add(id);
            hostNames.put(id, host.path(MonitoringConstants.HOST_FIELD).asText());
        }

        List<String> floatItems = new ArrayList<>();
        List<String> intItems = new ArrayList<>();

        Map<String, String> itemKeyMap = new HashMap<>();
        Map<String, String> itemHostMap = new HashMap<>();
        Map<String, String> itemNameMap = new HashMap<>();
        Map<String, Integer> itemValueTypeMap = new HashMap<>();
        Map<String, String> itemStatusMap = new HashMap<>();
        Map<String, String> itemUnitsMap = new HashMap<>();

        List<List<String>> hostBatches = chunkList(hostIds, resolveHostItemsBatchSize());
        AtomicReference<RuntimeException> lastBatchException = new AtomicReference<>();
        AtomicInteger successfulItemBatches = new AtomicInteger();
        AtomicInteger receivedItemsCount = new AtomicInteger();
        AtomicBoolean partialCollection = new AtomicBoolean(false);
        long startedAtMs = System.currentTimeMillis();
        long now = Instant.now().getEpochSecond();
        long lastSuccessfulMetricsClock = syncStateService.getLastSuccessfulMetricsClock();
        boolean fullResync = lastSuccessfulMetricsClock <= 0 || syncStateService.shouldRunFullResync("metrics");
        if (fullResync) {
            log.info(FULL_FETCH_START_LOG);
            syncStateService.markFullSyncDoneNow("metrics");
        } else {
            log.info(INCREMENTAL_FETCH_START_LOG);
            log.info(LASTCLOCK_USED_LOG, lastSuccessfulMetricsClock);
        }
        long windowStart = fullResync
                ? now - HISTORY_FALLBACK_WINDOW_SECONDS
                : Math.max(lastSuccessfulMetricsClock, now - HISTORY_FALLBACK_WINDOW_SECONDS);
        AtomicInteger skippedOldItemsCount = new AtomicInteger();
        AtomicInteger newItemsCount = new AtomicInteger();
        AtomicLong maxClockSeen = new AtomicLong(lastSuccessfulMetricsClock);

        Mono<Boolean> shouldSkipCollectionMono = fullResync
                ? Mono.just(false)
                : zabbixClient.getLatestMetricClock()
                .map(this::extractLatestItemClock)
                .map(latestClock -> {
                    if (latestClock <= 0L || latestClock > lastSuccessfulMetricsClock) {
                        return false;
                    }
                    log.info(NO_CHANGES_SKIP_LOG, latestClock, lastSuccessfulMetricsClock);
                    return true;
                })
                .onErrorResume(ex -> {
                    log.warn(LOG_PREFIX + "Latest-clock precheck failed, continuing incremental fetch: {}", ex.getMessage());
                    return Mono.just(false);
                });

        return shouldSkipCollectionMono.flatMap(shouldSkipCollection -> {
            if (shouldSkipCollection) {
                return Mono.just(new ZabbixMetricsCollectionResult(List.of(), false));
            }
            return Flux.fromIterable(hostBatches)
                .index()
                .concatMap(batchTuple -> processMetricBatch(
                        batchTuple.getT1().intValue(),
                        hostBatches.size(),
                        batchTuple.getT2(),
                        hostMap,
                        hostNames,
                        metricsByKey,
                        floatItems,
                        intItems,
                        itemKeyMap,
                        itemHostMap,
                        itemNameMap,
                        itemValueTypeMap,
                        itemStatusMap,
                        itemUnitsMap,
                        successfulItemBatches,
                        receivedItemsCount,
                        lastBatchException,
                        fullResync,
                        lastSuccessfulMetricsClock,
                        skippedOldItemsCount,
                        newItemsCount,
                        maxClockSeen
                ))
                .onErrorResume(exception -> {
                    partialCollection.set(successfulItemBatches.get() > 0 || !metricsByKey.isEmpty());
                    return Mono.empty();
                })
                .then(Mono.defer(() -> {
                    if (successfulItemBatches.get() == 0 && lastBatchException.get() != null) {
                        log.warn(NO_SUCCESSFUL_ITEMS_BATCHES_LOG);
                        return Mono.error(lastBatchException.get());
                    }

                    if (lastBatchException.get() != null && !metricsByKey.isEmpty()) {
                        partialCollection.set(true);
                        log.warn(LOG_PREFIX + "Partial Zabbix metrics collected after batch interruption; retaining {} metrics",
                                metricsByKey.size());
                    }

                    log.info(RECEIVED_ITEMS_LOG_TEMPLATE, receivedItemsCount.get());
                    log.info(FETCHED_ITEMS_LOG_TEMPLATE, floatItems.size(), intItems.size());

                    return processHistoryBatches(
                            floatItems,
                            0,
                            FLOAT_BATCH_ERROR_TEMPLATE,
                            "Metrics history collection interrupted, stopping remaining float history batches",
                            windowStart,
                            now,
                            metricsByKey,
                            itemKeyMap,
                            itemHostMap,
                            itemNameMap,
                            itemValueTypeMap,
                            itemStatusMap,
                            itemUnitsMap,
                            hostNames,
                            hostMap,
                            lastBatchException,
                            partialCollection
                    ).then(processHistoryBatches(
                            intItems,
                            3,
                            INT_BATCH_ERROR_TEMPLATE,
                            "Metrics history collection interrupted, stopping remaining int history batches",
                            windowStart,
                            now,
                            metricsByKey,
                            itemKeyMap,
                            itemHostMap,
                            itemNameMap,
                            itemValueTypeMap,
                            itemStatusMap,
                            itemUnitsMap,
                            hostNames,
                            hostMap,
                            lastBatchException,
                            partialCollection
                    )).onErrorResume(exception -> {
                        partialCollection.set(!metricsByKey.isEmpty());
                        return Mono.empty();
                    }).then(Mono.fromSupplier(() -> {
                        List<ZabbixMetricDTO> metrics = new ArrayList<>(metricsByKey.values());
                        log.info(METRIC_COUNT_LOG_TEMPLATE, metrics.size());
                        if (!fullResync && metrics.isEmpty()) {
                            log.info(FALLBACK_FULL_RESYNC_LOG, "empty incremental result");
                            syncStateService.requestFullSync("metrics");
                        }
                        log.info(ITEMS_SKIPPED_OLD_LOG, skippedOldItemsCount.get());
                        log.info(NEW_ITEMS_DETECTED_LOG, newItemsCount.get());
                        if (maxClockSeen.get() > lastSuccessfulMetricsClock) {
                            syncStateService.markMetricsClock(maxClockSeen.get());
                        }
                        long durationMs = System.currentTimeMillis() - startedAtMs;
                        int fetched = receivedItemsCount.get();
                        int skipped = skippedOldItemsCount.get();
                        double ratio = fetched <= 0 ? 0.0 : (double) newItemsCount.get() / (double) fetched;
                        long payloadApproxBytes = (long) fetched * 256L;
                        log.info(DELTA_FETCH_DONE_LOG, durationMs, fetched, skipped, String.format("%.4f", ratio), payloadApproxBytes);
                        if (partialCollection.get() && !metrics.isEmpty()) {
                            log.warn(LOG_PREFIX + "Partial Zabbix metrics collected");
                        }
                        return new ZabbixMetricsCollectionResult(List.copyOf(metrics), partialCollection.get());
                    }));
                }));
        });
    }

    private Mono<Void> processMetricBatch(
            int batchIndex,
            int totalBatches,
            List<String> hostBatch,
            Map<String, MonitoredHost> hostMap,
            Map<String, String> hostNames,
            Map<String, ZabbixMetricDTO> metricsByKey,
            List<String> floatItems,
            List<String> intItems,
            Map<String, String> itemKeyMap,
            Map<String, String> itemHostMap,
            Map<String, String> itemNameMap,
            Map<String, Integer> itemValueTypeMap,
            Map<String, String> itemStatusMap,
            Map<String, String> itemUnitsMap,
            AtomicInteger successfulItemBatches,
            AtomicInteger receivedItemsCount,
            AtomicReference<RuntimeException> lastBatchException,
            boolean fullResync,
            long lastSuccessfulMetricsClock,
            AtomicInteger skippedOldItemsCount,
            AtomicInteger newItemsCount,
            AtomicLong maxClockSeen
    ) {
        long batchStartedAt = System.nanoTime();
        log.info(FETCHING_ITEMS_BATCH_LOG_TEMPLATE, batchIndex + 1, totalBatches, hostBatch.size());

        Mono<JsonNode> itemsMono = fullResync
                ? zabbixClient.getItemsByHosts(hostBatch)
                : zabbixClient.getItemsByHostsIncremental(hostBatch);
        return itemsMono
                .doOnNext(items -> {
                    long durationMs = nanosToMillis(System.nanoTime() - batchStartedAt);
                    if (items == null || !items.isArray()) {
                        log.info(ITEMS_BATCH_DURATION_LOG_TEMPLATE, batchIndex + 1, totalBatches, durationMs);
                        return;
                    }

                    successfulItemBatches.incrementAndGet();
                    receivedItemsCount.addAndGet(items.size());
                    log.info(RECEIVED_ITEMS_BATCH_LOG_TEMPLATE, items.size(), hostBatch.size());

                    int mappedMetricsBeforeBatch = metricsByKey.size();
                    for (JsonNode item : items) {
                        String key = item.path(KEY_FIELD).asText();

                        if (!isUsefulMetric(key)) {
                            continue;
                        }

                        String itemId = item.path(ITEM_ID_FIELD).asText();
                        int valueType = item.path(VALUE_TYPE_FIELD).asInt();
                        String hostId = item.path(MonitoringConstants.HOST_ID_FIELD).asText();
                        long itemLastClock = item.path(LAST_CLOCK_FIELD).asLong(0L);
                        if (!fullResync && itemLastClock > 0 && itemLastClock <= lastSuccessfulMetricsClock) {
                            skippedOldItemsCount.incrementAndGet();
                            continue;
                        }
                        if (itemLastClock > 0) {
                            maxClockSeen.accumulateAndGet(itemLastClock, Math::max);
                        }
                        newItemsCount.incrementAndGet();
                        String itemName = item.path(MonitoringConstants.NAME_FIELD).asText(key);
                        String status = resolveMetricStatus(item);
                        String units = normalizeUnits(item.path(UNITS_FIELD).asText(null));

                        itemKeyMap.put(itemId, key);
                        itemHostMap.put(itemId, hostId);
                        itemNameMap.put(itemId, itemName);
                        itemValueTypeMap.put(itemId, valueType);
                        itemStatusMap.put(itemId, status);
                        itemUnitsMap.put(itemId, units);

                        ZabbixMetricDTO dto = buildMetricFromItem(
                                item,
                                hostNames,
                                hostMap,
                                itemName,
                                key,
                                valueType,
                                status,
                                units
                        );

                        if (dto != null) {
                            metricsByKey.put(metricMapKey(dto.getHostId(), dto.getItemId(), dto.getTimestamp()), dto);
                            continue;
                        }

                        if (valueType == 0) {
                            floatItems.add(itemId);
                        }
                        if (valueType == 3) {
                            intItems.add(itemId);
                        }
                    }

                    log.info(MAPPED_ITEMS_BATCH_LOG_TEMPLATE, metricsByKey.size() - mappedMetricsBeforeBatch);
                    log.info(ITEMS_BATCH_DURATION_LOG_TEMPLATE, batchIndex + 1, totalBatches, durationMs);
                })
                .onErrorResume(exception -> {
                    long durationMs = nanosToMillis(System.nanoTime() - batchStartedAt);
                    RuntimeException runtimeException = exception instanceof RuntimeException re
                            ? re
                            : new RuntimeException(exception);
                    lastBatchException.set(runtimeException);

                    if (IntegrationClientSupport.containsInterruptedException(exception)) {
                        log.warn(LOG_PREFIX + "Metrics collection interrupted, stopping remaining batches: {}",
                                exception.getMessage());
                        return Mono.error(runtimeException);
                    }
                    if (IntegrationClientSupport.isTimeoutException(exception)) {
                        log.warn(TIMED_OUT_ITEMS_BATCH_LOG_TEMPLATE + ": {}", hostBatch, durationMs, exception.getMessage());
                        return Mono.error(runtimeException);
                    }
                    log.error(FAILED_ITEMS_BATCH_LOG_TEMPLATE + ": {}", hostBatch, durationMs, exception.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> processHistoryBatches(
            List<String> itemIds,
            int valueType,
            String batchErrorTemplate,
            String interruptionLogMessage,
            long windowStart,
            long now,
            Map<String, ZabbixMetricDTO> metricsByKey,
            Map<String, String> itemKeyMap,
            Map<String, String> itemHostMap,
            Map<String, String> itemNameMap,
            Map<String, Integer> itemValueTypeMap,
            Map<String, String> itemStatusMap,
            Map<String, String> itemUnitsMap,
            Map<String, String> hostNames,
            Map<String, MonitoredHost> hostMap,
            AtomicReference<RuntimeException> lastBatchException,
            AtomicBoolean partialCollection
    ) {
        if (itemIds.isEmpty()) {
            return Mono.empty();
        }

        log.info(FALLBACK_HISTORY_LOG_TEMPLATE, itemIds.size());

        return Flux.fromIterable(chunkList(itemIds, HISTORY_BATCH_SIZE))
                .concatMap(batch -> zabbixClient.getHistoryBatch(batch, valueType, windowStart, now)
                        .doOnNext(history -> mapHistoryFallback(
                                metricsByKey,
                                history,
                                itemKeyMap,
                                itemHostMap,
                                itemNameMap,
                                itemValueTypeMap,
                                itemStatusMap,
                                itemUnitsMap,
                            hostNames,
                            hostMap
                        ))
                        .onErrorResume(exception -> {
                            if (IntegrationClientSupport.containsInterruptedException(exception)) {
                                log.warn(LOG_PREFIX + interruptionLogMessage + ": {}", exception.getMessage());
                                return Mono.error(exception);
                            }
                            if (IntegrationClientSupport.isTimeoutException(exception)) {
                                RuntimeException runtimeException = exception instanceof RuntimeException re
                                        ? re
                                        : new RuntimeException(exception);
                                lastBatchException.set(runtimeException);
                                partialCollection.set(!metricsByKey.isEmpty());
                                log.warn(batchErrorTemplate + ": {}", batch.size(), exception.getMessage());
                                return Mono.error(runtimeException);
                            }
                            log.error(batchErrorTemplate + ": {}", batch.size(), exception.getMessage());
                            return Mono.empty();
                        }))
                .then();
    }

    /**
     * Check if a metric key is useful (not internal Zabbix metric).
     *
     * @param key metric key
     * @return true if useful
     */
    public boolean isUsefulMetric(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        String normalized = key.toLowerCase();
        return normalized.startsWith("system.cpu.util")
                || normalized.startsWith("system.cpu.load")
                || normalized.startsWith("system.cpu.num")
                || normalized.startsWith("vm.memory.util")
                || normalized.startsWith("vm.memory.size")
                || normalized.startsWith("system.swap")
                || normalized.startsWith("icmpping")
                || normalized.startsWith("icmppingloss")
                || normalized.startsWith("icmppingsec")
                || normalized.startsWith("vfs.fs")
                || normalized.startsWith("net.if.in")
                || normalized.startsWith("net.if.out")
                || normalized.startsWith("system.uptime")
                || normalized.startsWith("proc.num")
                || normalized.startsWith("agent.ping");
    }

    private void mapHistoryFallback(
            Map<String, ZabbixMetricDTO> metricsByKey,
            JsonNode history,
            Map<String, String> itemKeyMap,
            Map<String, String> itemHostMap,
            Map<String, String> itemNameMap,
            Map<String, Integer> itemValueTypeMap,
            Map<String, String> itemStatusMap,
            Map<String, String> itemUnitsMap,
            Map<String, String> hostNames,
            Map<String, MonitoredHost> hostMap
    ) {
        if (history == null || !history.isArray()) {
            return;
        }

        Map<String, JsonNode> latestHistoryPointByItem = new HashMap<>();

        for (JsonNode point : history) {
            String itemId = point.path(ITEM_ID_FIELD).asText();
            if (itemId == null || itemId.isBlank()) {
                continue;
            }

            JsonNode existing = latestHistoryPointByItem.get(itemId);
            if (existing == null || point.path(MonitoringConstants.CLOCK_FIELD).asLong(0L) > existing.path(MonitoringConstants.CLOCK_FIELD).asLong(0L)) {
                latestHistoryPointByItem.put(itemId, point);
            }
        }

        for (Map.Entry<String, JsonNode> entry : latestHistoryPointByItem.entrySet()) {
            String itemId = entry.getKey();
            JsonNode point = entry.getValue();
            String hostId = itemHostMap.get(itemId);
            if (hostId == null || hostId.isBlank()) {
                continue;
            }

            Long timestamp = parseEpoch(point.path(MonitoringConstants.CLOCK_FIELD).asText(null));
            Double value = parseNumericValue(point.path("value").asText(null));
            if (timestamp == null || value == null) {
                continue;
            }

            MonitoredHost monitoredHost = hostMap.get(hostId);
            ZabbixMetricDTO dto = ZabbixMetricDTO.builder()
                    .hostId(hostId)
                    .hostName(hostNames.getOrDefault(hostId, MonitoringConstants.UNKNOWN))
                    .itemId(itemId)
                    .metricName(itemNameMap.get(itemId))
                    .metricKey(itemKeyMap.get(itemId))
                    .source(UNKNOWN_SOURCE_LABEL)
                    .valueType(itemValueTypeMap.get(itemId))
                    .status(itemStatusMap.get(itemId))
                    .units(itemUnitsMap.get(itemId))
                    .value(value)
                    .timestamp(timestamp)
                    .ip(monitoredHost != null ? monitoredHost.getIp() : null)
                    .port(monitoredHost != null ? monitoredHost.getPort() : null)
                    .build();

            metricsByKey.put(metricMapKey(dto.getHostId(), dto.getItemId(), dto.getTimestamp()), dto);
        }
    }

    private ZabbixMetricDTO buildMetricFromItem(
            JsonNode item,
            Map<String, String> hostNames,
            Map<String, MonitoredHost> hostMap,
            String itemName,
            String key,
            int valueType,
            String status,
            String units
    ) {
        String hostId = item.path(MonitoringConstants.HOST_ID_FIELD).asText(null);
        String itemId = item.path(ITEM_ID_FIELD).asText(null);
        Long timestamp = parseEpoch(item.path(LAST_CLOCK_FIELD).asText(null));
        Double value = parseNumericValue(item.path(LAST_VALUE_FIELD).asText(null));

        if (hostId == null || hostId.isBlank() || itemId == null || itemId.isBlank() || timestamp == null || value == null) {
            return null;
        }

        MonitoredHost monitoredHost = hostMap.get(hostId);
        return ZabbixMetricDTO.builder()
                .hostId(hostId)
                .hostName(hostNames.getOrDefault(hostId, MonitoringConstants.UNKNOWN))
                .itemId(itemId)
                .metricName(itemName)
                .metricKey(key)
                .source(UNKNOWN_SOURCE_LABEL)
                .valueType(valueType)
                .status(status)
                .units(units)
                .value(value)
                .timestamp(timestamp)
                .ip(monitoredHost != null ? monitoredHost.getIp() : null)
                .port(monitoredHost != null ? monitoredHost.getPort() : null)
                .build();
    }

    private String resolveMetricStatus(JsonNode item) {
        int itemStatus = item.path(STATUS_FIELD).asInt(1);
        int itemState = item.path(STATE_FIELD).asInt(0);
        if (itemStatus == 0 && itemState == 0) {
            return MonitoringConstants.STATUS_UP;
        }
        if (itemStatus == 0) {
            return "UNSUPPORTED";
        }
        return MonitoringConstants.STATUS_DOWN;
    }

    private Long parseEpoch(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(raw);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private long extractLatestItemClock(JsonNode items) {
        if (items == null || !items.isArray() || items.isEmpty()) {
            return 0L;
        }
        Long parsed = parseEpoch(items.get(0).path(LAST_CLOCK_FIELD).asText(null));
        return parsed != null ? parsed : 0L;
    }

    private Double parseNumericValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String metricMapKey(String hostId, String itemId, Long timestamp) {
        return hostId + ":" + itemId + ":" + timestamp;
    }

    private String normalizeUnits(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private int resolveHostItemsBatchSize() {
        return configuredHostItemsBatchSize > 0 ? configuredHostItemsBatchSize : DEFAULT_HOST_ITEMS_BATCH_SIZE;
    }

    private long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    private <T> T await(Mono<T> mono) {
        try {
            return mono.toFuture().join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }
    }

    private List<List<String>> chunkList(List<String> items, int size) {
        List<List<String>> batches = new ArrayList<>();
        if (items == null || items.isEmpty() || size <= 0) {
            return batches;
        }
        for (int i = 0; i < items.size(); i += size) {
            batches.add(items.subList(i, Math.min(items.size(), i + size)));
        }
        return batches;
    }
}
