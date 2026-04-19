package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.iteam.domain.MonitoredHost;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZabbixMetricDTO;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.service.ZabbixSyncService;
import tn.iteam.util.MonitoringConstants;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ZabbixAdapter {

    private static final Logger log = LoggerFactory.getLogger(ZabbixAdapter.class);
    private static final int HISTORY_BATCH_SIZE = 50;
    private static final long HISTORY_WINDOW_SECONDS = 60;
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
    private static final String LOG_PREFIX = "[ZABBIX] ";
    private static final String FLOAT_BATCH_ERROR_TEMPLATE = LOG_PREFIX + "Failed float history batch size={}, skipping batch";
    private static final String INT_BATCH_ERROR_TEMPLATE = LOG_PREFIX + "Failed int history batch size={}, skipping batch";
    private static final String METRIC_COUNT_LOG_TEMPLATE = LOG_PREFIX + "Final mapped metrics count={}";
    private static final String FETCHED_ITEMS_LOG_TEMPLATE = LOG_PREFIX + "Useful items fetched: float={}, int={}";
    private static final String MISSING_CLOCK_LOG_TEMPLATE = "Problem event {} missing clock from Zabbix payload";
    private static final String UNKNOWN_SOURCE_LABEL = MonitoringConstants.SOURCE_LABEL_ZABBIX;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ZabbixSyncService syncService;
    private final ZabbixClient zabbixClient;

    private String formatDate(long epoch) {
        return Instant.ofEpochSecond(epoch)
                .atZone(ZoneId.systemDefault())
                .format(DATE_TIME_FORMATTER);
    }

    // ================== HOSTS ==================
    public List<ServiceStatusDTO> fetchAll() {
        JsonNode hosts = zabbixClient.getHosts();
        List<ServiceStatusDTO> dtos = new ArrayList<>();

        if (hosts == null || !hosts.isArray()) {
            return dtos;
        }

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
        return fetchProblems(zabbixClient.getHosts());
    }

    public List<ZabbixProblemDTO> fetchProblems(JsonNode hosts) {
        Map<String, JsonNode> hostMapById = buildHostMap(hosts);

        JsonNode problemsJson = zabbixClient.getRecentProblems();

        List<ZabbixProblemDTO> dtos = new ArrayList<>();

        if (problemsJson == null || !problemsJson.isArray()) {
            return dtos;
        }

        for (JsonNode node : problemsJson) {
            String hostId = null;
            JsonNode hostRefs = node.path(HOSTS_FIELD);
            if (hostRefs.isArray() && !hostRefs.isEmpty()) {
                hostId = hostRefs.get(0).path(MonitoringConstants.HOST_ID_FIELD).asText(null);
            }
            if (hostId == null || hostId.isBlank()) {
                hostId = node.path(MonitoringConstants.HOST_ID_FIELD).asText(null);
            }
            if (hostId == null || hostId.isBlank()) {
                JsonNode trigger = fetchTriggerById(node.path(OBJECT_ID_FIELD).asText(null));
                hostId = extractHostIdFromTrigger(trigger);
            }

            JsonNode fullHost = hostId != null ? hostMapById.get(hostId) : null;
            if (fullHost == null && hostId != null) {
                JsonNode hostById = zabbixClient.getHostById(hostId);
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

    private JsonNode fetchTriggerById(String triggerId) {
        if (triggerId == null || triggerId.isBlank()) {
            return null;
        }
        return zabbixClient.getTriggerById(triggerId);
    }

    private String extractHostIdFromTrigger(JsonNode trigger) {
        if (trigger == null || !trigger.isArray() || trigger.isEmpty()) {
            return null;
        }
        JsonNode hosts = trigger.get(0).path(HOSTS_FIELD);
        if (!hosts.isArray() || hosts.isEmpty()) {
            return null;
        }
        return hosts.get(0).path(MonitoringConstants.HOST_ID_FIELD).asText(null);
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
    public List<ZabbixMetricDTO> fetchMetricsAndMap() {
        return fetchMetricsAndMap(zabbixClient.getHosts());
    }

    public List<ZabbixMetricDTO> fetchMetricsAndMap(JsonNode hosts) {
        Map<String, MonitoredHost> hostMap = syncService.loadHostMap(hosts);
        List<ZabbixMetricDTO> metrics = new ArrayList<>();

        if (hosts == null || !hosts.isArray()) {
            return metrics;
        }

        List<String> hostIds = new ArrayList<>();
        Map<String, String> hostNames = new HashMap<>();

        for (JsonNode host : hosts) {
            String id = host.path(MonitoringConstants.HOST_ID_FIELD).asText();
            hostIds.add(id);
            hostNames.put(id, host.path(MonitoringConstants.HOST_FIELD).asText());
        }

        JsonNode items = zabbixClient.getItemsByHosts(hostIds);
        if (items == null || !items.isArray()) {
            return metrics;
        }

        List<String> floatItems = new ArrayList<>();
        List<String> intItems = new ArrayList<>();

        Map<String, String> itemKeyMap = new HashMap<>();
        Map<String, String> itemHostMap = new HashMap<>();

        for (JsonNode item : items) {
            String key = item.path(KEY_FIELD).asText();

            if (!isUsefulMetric(key)) {
                continue;
            }

            String itemId = item.path(ITEM_ID_FIELD).asText();
            int valueType = item.path(VALUE_TYPE_FIELD).asInt();
            String hostId = item.path(MonitoringConstants.HOST_ID_FIELD).asText();

            itemKeyMap.put(itemId, key);
            itemHostMap.put(itemId, hostId);

            if (valueType == 0) floatItems.add(itemId);
            if (valueType == 3) intItems.add(itemId);
        }

        long now = Instant.now().getEpochSecond();
        long windowStart = now - HISTORY_WINDOW_SECONDS;

        log.info(FETCHED_ITEMS_LOG_TEMPLATE, floatItems.size(), intItems.size());

        if (!floatItems.isEmpty()) {
            for (List<String> batch : chunkList(floatItems, HISTORY_BATCH_SIZE)) {
                try {
                    JsonNode history = zabbixClient.getHistoryBatch(batch, 0, windowStart, now);
                    mapHistory(metrics, history, itemKeyMap, itemHostMap, hostNames, hostMap);
                } catch (RuntimeException e) {
                    log.error(FLOAT_BATCH_ERROR_TEMPLATE, batch.size(), e);
                }
            }
        }

        if (!intItems.isEmpty()) {
            for (List<String> batch : chunkList(intItems, HISTORY_BATCH_SIZE)) {
                try {
                    JsonNode history = zabbixClient.getHistoryBatch(batch, 3, windowStart, now);
                    mapHistory(metrics, history, itemKeyMap, itemHostMap, hostNames, hostMap);
                } catch (RuntimeException e) {
                    log.error(INT_BATCH_ERROR_TEMPLATE, batch.size(), e);
                }
            }
        }

        log.info(METRIC_COUNT_LOG_TEMPLATE, metrics.size());
        return metrics;
    }

    private boolean isUsefulMetric(String key) {
        return key.startsWith("system.cpu.util")
                || key.startsWith("vm.memory.util")
                || key.startsWith("icmpping")
                || key.startsWith("icmppingloss")
                || key.startsWith("icmppingsec")
                || key.startsWith("vfs.fs");
    }

    private void mapHistory(
            List<ZabbixMetricDTO> metrics,
            JsonNode history,
            Map<String, String> itemKeyMap,
            Map<String, String> itemHostMap,
            Map<String, String> hostNames,
            Map<String, MonitoredHost> hostMap
    ) {
        if (history == null || !history.isArray()) {
            return;
        }

        for (JsonNode point : history) {
            String itemId = point.path(ITEM_ID_FIELD).asText();
            String hostId = itemHostMap.get(itemId);
            if (hostId == null) {
                continue;
            }
            MonitoredHost mh = hostMap.get(hostId);

            metrics.add(
                    ZabbixMetricDTO.builder()
                            .hostId(hostId)
                            .hostName(hostNames.getOrDefault(hostId, MonitoringConstants.UNKNOWN))
                            .ip(mh != null ? mh.getIp() : null)
                            .port(mh != null ? mh.getPort() : null)
                            .itemId(itemId)
                            .metricKey(itemKeyMap.get(itemId))
                            .value(point.path(VALUE_FIELD).asText().isEmpty() ? 0.0 : point.path(VALUE_FIELD).asDouble())
                            .timestamp(point.path(MonitoringConstants.CLOCK_FIELD).asLong())
                            .build()
            );
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
