package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tn.iteam.domain.MonitoredHost;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZabbixMetricDTO;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.service.ZabbixSyncService;
import tn.iteam.util.MonitoringConstants;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class ZabbixAdapter {

    private static final Logger log = LoggerFactory.getLogger(ZabbixAdapter.class);
    private static final int PROBLEM_TRIGGER_BATCH_SIZE = 25;
    private static final int HISTORY_BATCH_SIZE = 50;
    private static final long HISTORY_FALLBACK_WINDOW_SECONDS = 3600;
    private static final int DEFAULT_HOST_ITEMS_BATCH_SIZE = 2;
    private static final int DEFAULT_MAIN_INTERFACE_FLAG = 1;
    private static final int DEFAULT_HOST_STATUS = 1;
    private static final int DEFAULT_ZABBIX_PORT = 10050;
    private static final int RESOLVED_AT_ZERO = 0;
    private static final String INTERFACES_FIELD = "interfaces";
    private static final String HOSTS_FIELD = "hosts";
    private static final String OBJECT_ID_FIELD = "objectid";
    private static final String RESOLVED_CLOCK_FIELD = "r_clock";
    private static final String SEVERITY_FIELD = "severity";
    private static final String VALUE_FIELD = "value";
    private static final String KEY_FIELD = "key_";
    private static final String ITEM_ID_FIELD = "itemid";
    private static final String VALUE_TYPE_FIELD = "value_type";
    private static final String LAST_VALUE_FIELD = "lastvalue";
    private static final String LAST_CLOCK_FIELD = "lastclock";
    private static final String STATUS_FIELD = "status";
    private static final String STATE_FIELD = "state";
    private static final String UNITS_FIELD = "units";
    private static final String ITEMS_FIELD = "items";
    private static final String LOG_PREFIX = "[ZABBIX] ";
    private static final String FLOAT_BATCH_ERROR_TEMPLATE = LOG_PREFIX + "Failed float history batch size={}, skipping batch";
    private static final String INT_BATCH_ERROR_TEMPLATE = LOG_PREFIX + "Failed int history batch size={}, skipping batch";
    private static final String METRIC_COUNT_LOG_TEMPLATE = LOG_PREFIX + "Final mapped metrics count={}";
    private static final String FETCHED_ITEMS_LOG_TEMPLATE = LOG_PREFIX + "Useful items fetched: float={}, int={}";
    private static final String MISSING_CLOCK_LOG_TEMPLATE = "Problem event {} missing clock from Zabbix payload";
    private static final String SKIPPED_PROBLEM_NO_HOST_TEMPLATE = LOG_PREFIX + "Skipped problem eventId={} because hostId is null";
    private static final String RECEIVED_PROBLEMS_LOG_TEMPLATE = LOG_PREFIX + "Received {} problems from Zabbix API";
    private static final String RECEIVED_ITEMS_LOG_TEMPLATE = LOG_PREFIX + "Received {} items from Zabbix API";
    private static final String RECEIVED_ITEMS_BATCH_LOG_TEMPLATE = LOG_PREFIX + "Received {} items from Zabbix API for host batch size={}";
    private static final String FETCHING_ITEMS_BATCH_LOG_TEMPLATE = LOG_PREFIX + "Fetching Zabbix metrics batch {}/{} with {} hosts";
    private static final String ITEMS_BATCH_DURATION_LOG_TEMPLATE = LOG_PREFIX + "Finished metrics batch {}/{} in {} ms";
    private static final String MAPPED_ITEMS_BATCH_LOG_TEMPLATE = LOG_PREFIX + "Mapped {} metrics from current batch";
    private static final String TIMED_OUT_ITEMS_BATCH_LOG_TEMPLATE = LOG_PREFIX + "Batch timeout for hosts {} after {} ms";
    private static final String FAILED_ITEMS_BATCH_LOG_TEMPLATE = LOG_PREFIX + "Batch failed for hosts {} after {} ms";
    private static final String NO_SUCCESSFUL_ITEMS_BATCHES_LOG = LOG_PREFIX + "No Zabbix metrics batch completed successfully";
    private static final String FALLBACK_HISTORY_LOG_TEMPLATE = LOG_PREFIX + "Loading fallback history for {} items";
    private static final String UNKNOWN_SOURCE_LABEL = MonitoringConstants.SOURCE_LABEL_ZABBIX;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ZabbixSyncService syncService;
    private final ZabbixClient zabbixClient;

    @Value("${zabbix.metrics.host-batch-size:2}")
    private int configuredHostItemsBatchSize;

    private String formatDate(long epoch) {
        return Instant.ofEpochSecond(epoch)
                .atZone(ZoneId.systemDefault())
                .format(DATE_TIME_FORMATTER);
    }

    // ================== HOSTS ==================
    public List<ServiceStatusDTO> fetchAll() {
        JsonNode hosts = await(zabbixClient.getHosts());
        List<ServiceStatusDTO> dtos = new ArrayList<>();

        if (hosts == null || !hosts.isArray()) {
            return dtos;
        }

        log.info(LOG_PREFIX + "Received {} hosts from Zabbix", hosts.size());

        for (JsonNode hostNode : hosts) {
            ServiceStatusDTO dto = new ServiceStatusDTO();
            dto.setSource(MonitoringConstants.SOURCE_ZABBIX);
            dto.setName(hostNode.path(MonitoringConstants.HOST_FIELD).asText(MonitoringConstants.UNKNOWN));
            dto.setIp(extractMainIp(hostNode));
            dto.setPort(extractMainPort(hostNode));
            dto.setProtocol(MonitoringConstants.PROTOCOL_HTTP);
            dto.setStatus(hostNode.path(MonitoringConstants.STATUS_FIELD).asInt(DEFAULT_HOST_STATUS) == 0
                    ? MonitoringConstants.STATUS_UP
                    : MonitoringConstants.STATUS_DOWN);
            dto.setCategory(MonitoringConstants.CATEGORY_SERVER);
            dtos.add(dto);
        }

        return dtos;
    }

    private String extractMainIp(JsonNode hostNode) {
        for (JsonNode iface : hostNode.path(INTERFACES_FIELD)) {
            if (iface.path(MonitoringConstants.MAIN_FIELD).asInt(0) == DEFAULT_MAIN_INTERFACE_FLAG) {
                return iface.path(MonitoringConstants.IP_FIELD).asText(MonitoringConstants.IP_UNKNOWN);
            }
        }
        return MonitoringConstants.IP_UNKNOWN;
    }

    private Integer extractMainPort(JsonNode hostNode) {
        for (JsonNode iface : hostNode.path(INTERFACES_FIELD)) {
            if (iface.path(MonitoringConstants.MAIN_FIELD).asInt(0) == DEFAULT_MAIN_INTERFACE_FLAG) {
                return iface.path(MonitoringConstants.PORT_FIELD).asInt(DEFAULT_ZABBIX_PORT);
            }
        }
        return DEFAULT_ZABBIX_PORT;
    }

    // ================== PROBLEMS ==================
    public List<ZabbixProblemDTO> fetchProblems() {
        return fetchProblems(await(zabbixClient.getHosts()));
    }

    public List<ZabbixProblemDTO> fetchProblems(JsonNode hosts) {
        Map<String, JsonNode> hostMapById = buildHostMap(hosts);

        JsonNode problemsJson = await(zabbixClient.getRecentProblems());

        List<ZabbixProblemDTO> dtos = new ArrayList<>();

        if (problemsJson == null || !problemsJson.isArray()) {
            return dtos;
        }

        log.info(RECEIVED_PROBLEMS_LOG_TEMPLATE, problemsJson.size());

        Map<String, JsonNode> triggersById = preloadTriggersForProblems(problemsJson);

        for (JsonNode node : problemsJson) {
            String hostId = resolveProblemHostId(node, hostMapById, triggersById);
            if (hostId == null || hostId.isBlank()) {
                log.warn(SKIPPED_PROBLEM_NO_HOST_TEMPLATE, node.path(MonitoringConstants.EVENT_ID_FIELD).asText());
                continue;
            }

            JsonNode fullHost = hostMapById.get(hostId);
            if (fullHost == null) {
                JsonNode hostById = await(zabbixClient.getHostById(hostId));
                if (hostById != null && hostById.isArray() && !hostById.isEmpty()) {
                    fullHost = hostById.get(0);
                    hostMapById.put(hostId, fullHost);
                }
            }

            long startedAt = node.path(MonitoringConstants.CLOCK_FIELD).asLong(0L);
            long resolvedAt = node.path(RESOLVED_CLOCK_FIELD).asLong(0L);

            boolean isResolved = resolvedAt > 0;

            if (startedAt <= 0) {
                log.warn(MISSING_CLOCK_LOG_TEMPLATE, node.path(MonitoringConstants.EVENT_ID_FIELD).asText());
            }

            dtos.add(
                    ZabbixProblemDTO.builder()
                            .problemId(node.path(MonitoringConstants.EVENT_ID_FIELD).asText())
                            .host(fullHost != null ? fullHost.path(MonitoringConstants.HOST_FIELD).asText() : MonitoringConstants.UNKNOWN)
                            .hostId(hostId)
                            .description(node.path(MonitoringConstants.NAME_FIELD).asText())
                            .severity(node.path(SEVERITY_FIELD).asText())
                            .active(!isResolved)
                            .source(UNKNOWN_SOURCE_LABEL)
                            .eventId(node.path(MonitoringConstants.EVENT_ID_FIELD).asLong())
                            .ip(fullHost != null ? extractMainIp(fullHost) : null)
                            .port(fullHost != null ? extractMainPort(fullHost) : null)
                            .startedAt(startedAt)
                            .startedAtFormatted(formatDate(startedAt))
                            .resolvedAt(resolvedAt == RESOLVED_AT_ZERO ? null : resolvedAt)
                            .resolvedAtFormatted(resolvedAt == RESOLVED_AT_ZERO ? null : formatDate(resolvedAt))
                            .status(isResolved ? MonitoringConstants.STATUS_RESOLVED : MonitoringConstants.STATUS_ACTIVE)
                            .build()
            );
        }

        return dtos;
    }

    private Map<String, JsonNode> preloadTriggersForProblems(JsonNode problemsJson) {
        Map<String, JsonNode> triggersById = new HashMap<>();
        List<String> missingTriggerIds = new ArrayList<>();

        for (JsonNode node : problemsJson) {
            if (extractHostIdFromProblemNode(node) != null) {
                continue;
            }

            String triggerId = node.path(OBJECT_ID_FIELD).asText(null);
            if (triggerId != null && !triggerId.isBlank() && !triggersById.containsKey(triggerId)) {
                missingTriggerIds.add(triggerId);
                triggersById.put(triggerId, null);
            }
        }

        for (List<String> batch : chunkList(missingTriggerIds, PROBLEM_TRIGGER_BATCH_SIZE)) {
            JsonNode triggerBatch = await(zabbixClient.getTriggersByIds(batch));
            if (triggerBatch == null || !triggerBatch.isArray()) {
                continue;
            }

            for (JsonNode triggerNode : triggerBatch) {
                String triggerId = triggerNode.path("triggerid").asText(null);
                if (triggerId != null && !triggerId.isBlank()) {
                    triggersById.put(triggerId, triggerNode);
                }
            }
        }

        return triggersById;
    }

    private String resolveProblemHostId(JsonNode node, Map<String, JsonNode> hostMapById, Map<String, JsonNode> triggersById) {
        String hostId = extractHostIdFromProblemNode(node);
        if (hostId != null && !hostId.isBlank()) {
            return hostId;
        }

        String triggerId = node.path(OBJECT_ID_FIELD).asText(null);
        JsonNode trigger = triggersById.get(triggerId);
        hostId = extractHostIdFromTrigger(trigger);
        if (hostId != null && !hostId.isBlank()) {
            return hostId;
        }

        JsonNode availableHosts = hostMapById.get(node.path(MonitoringConstants.HOST_ID_FIELD).asText());
        if (availableHosts != null) {
            return availableHosts.path(MonitoringConstants.HOST_ID_FIELD).asText(null);
        }

        return null;
    }

    private String extractHostIdFromProblemNode(JsonNode node) {
        JsonNode hostRefs = node.path(HOSTS_FIELD);
        if (hostRefs.isArray() && !hostRefs.isEmpty()) {
            String hostId = hostRefs.get(0).path(MonitoringConstants.HOST_ID_FIELD).asText(null);
            if (hostId != null && !hostId.isBlank()) {
                return hostId;
            }
        }

        String directHostId = node.path(MonitoringConstants.HOST_ID_FIELD).asText(null);
        if (directHostId != null && !directHostId.isBlank()) {
            return directHostId;
        }

        return null;
    }

    private String extractHostIdFromTrigger(JsonNode trigger) {
        if (trigger == null || trigger.isMissingNode() || trigger.isNull()) {
            return null;
        }

        JsonNode triggerNode = trigger;
        if (trigger.isArray()) {
            if (trigger.isEmpty()) {
                return null;
            }
            triggerNode = trigger.get(0);
        }

        JsonNode hosts = triggerNode.path(HOSTS_FIELD);
        if (hosts.isArray() && !hosts.isEmpty()) {
            String hostId = hosts.get(0).path(MonitoringConstants.HOST_ID_FIELD).asText(null);
            if (hostId != null && !hostId.isBlank()) {
                return hostId;
            }
        }

        JsonNode items = triggerNode.path(ITEMS_FIELD);
        if (items.isArray() && !items.isEmpty()) {
            String hostId = items.get(0).path(MonitoringConstants.HOST_ID_FIELD).asText(null);
            if (hostId != null && !hostId.isBlank()) {
                return hostId;
            }
        }

        return null;
    }

    private Map<String, JsonNode> buildHostMap(JsonNode hosts) {
        Map<String, JsonNode> map = new HashMap<>();

        if (hosts != null && hosts.isArray()) {
            for (JsonNode h : hosts) {
                map.put(h.path(MonitoringConstants.HOST_ID_FIELD).asText(), h);
            }
        }

        return map;
    }

    // ================== METRICS ==================
    public Mono<List<ZabbixMetricDTO>> fetchMetricsAndMap() {
        return zabbixClient.getHosts().flatMap(this::fetchMetricsAndMap);
    }

    public Mono<List<ZabbixMetricDTO>> fetchMetricsAndMap(JsonNode hosts) {
        if (hosts == null || !hosts.isArray()) {
            return Mono.just(List.of());
        }

        return Mono.fromCallable(() -> syncService.loadHostMap(hosts))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(hostMap -> {
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
                    long now = Instant.now().getEpochSecond();
                    long windowStart = now - HISTORY_FALLBACK_WINDOW_SECONDS;

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
                                    lastBatchException
                            ))
                            .then(Mono.defer(() -> {
                                if (successfulItemBatches.get() == 0 && lastBatchException.get() != null) {
                                    log.warn(NO_SUCCESSFUL_ITEMS_BATCHES_LOG);
                                    return Mono.error(lastBatchException.get());
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
                                        hostMap
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
                                        hostMap
                                )).then(Mono.fromSupplier(() -> {
                                    List<ZabbixMetricDTO> metrics = new ArrayList<>(metricsByKey.values());
                                    log.info(METRIC_COUNT_LOG_TEMPLATE, metrics.size());
                                    return metrics;
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
            AtomicReference<RuntimeException> lastBatchException
    ) {
        long batchStartedAt = System.nanoTime();
        log.info(FETCHING_ITEMS_BATCH_LOG_TEMPLATE, batchIndex + 1, totalBatches, hostBatch.size());

        return zabbixClient.getItemsByHosts(hostBatch)
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

                    if (containsInterruptedException(exception)) {
                        log.warn(LOG_PREFIX + "Metrics collection interrupted, stopping remaining batches", exception);
                        return Mono.error(runtimeException);
                    }
                    if (isTimeoutException(exception)) {
                        log.warn(TIMED_OUT_ITEMS_BATCH_LOG_TEMPLATE, hostBatch, durationMs, exception);
                    } else {
                        log.error(FAILED_ITEMS_BATCH_LOG_TEMPLATE, hostBatch, durationMs, exception);
                    }
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
            Map<String, MonitoredHost> hostMap
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
                            if (containsInterruptedException(exception)) {
                                log.warn(LOG_PREFIX + interruptionLogMessage, exception);
                                return Mono.error(exception);
                            }
                            log.error(batchErrorTemplate, batch.size(), exception);
                            return Mono.empty();
                        }))
                .then();
    }

    private boolean isUsefulMetric(String key) {
        return key.startsWith("system.cpu.util")
                || key.startsWith("vm.memory.util")
                || key.startsWith("icmpping")
                || key.startsWith("icmppingloss")
                || key.startsWith("icmppingsec")
                || key.startsWith("vfs.fs");
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
            Double value = parseNumericValue(point.path(VALUE_FIELD).asText(null));
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

    private boolean isTimeoutException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof IntegrationTimeoutException) {
                return true;
            }
            if ("io.netty.handler.timeout.ReadTimeoutException".equals(current.getClass().getName())) {
                return true;
            }
            if ("io.netty.channel.ConnectTimeoutException".equals(current.getClass().getName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsInterruptedException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
