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

import java.time.Instant;
import java.util.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
@Component
@RequiredArgsConstructor
public class ZabbixAdapter {

    private static final Logger log = LoggerFactory.getLogger(ZabbixAdapter.class);
    private static final int HISTORY_BATCH_SIZE = 50;
    private static final long HISTORY_WINDOW_SECONDS = 60;

    private final ZabbixSyncService syncService;
    private final ZabbixClient zabbixClient;


    private String formatDate(long epoch) {
        return Instant.ofEpochSecond(epoch)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    // ================== HOSTS ==================
    public List<ServiceStatusDTO> fetchAll() {

        Map<String, MonitoredHost> hostMap = syncService.loadHostMap();

        JsonNode hosts = zabbixClient.getHosts();
        List<ServiceStatusDTO> dtos = new ArrayList<>();

        if (hosts == null || !hosts.isArray()) return dtos;

        for (JsonNode hostNode : hosts) {

            ServiceStatusDTO dto = new ServiceStatusDTO();

            dto.setSource("ZABBIX");
            dto.setName(hostNode.path("host").asText("UNKNOWN"));
            dto.setIp(extractMainIp(hostNode));
            dto.setPort(extractMainPort(hostNode)); //  FIX
            dto.setProtocol("HTTP");
            dto.setStatus(hostNode.path("status").asInt(1) == 0 ? "UP" : "DOWN");
            dto.setCategory("SERVER");

            dtos.add(dto);
        }

        return dtos;
    }

    private String extractMainIp(JsonNode hostNode) {
        for (JsonNode iface : hostNode.path("interfaces")) {
            if (iface.path("main").asInt(0) == 1) {
                return iface.path("ip").asText("IP_UNKNOWN");
            }
        }
        return "IP_UNKNOWN";
    }

    private Integer extractMainPort(JsonNode hostNode) {
        for (JsonNode iface : hostNode.path("interfaces")) {
            if (iface.path("main").asInt(0) == 1) {
                return iface.path("port").asInt(10050);
            }
        }
        return 10050;
    }

    // ================== PROBLEMS ==================
    public List<ZabbixProblemDTO> fetchProblems() {
        return fetchProblems(zabbixClient.getHosts());
    }

    public List<ZabbixProblemDTO> fetchProblems(JsonNode hosts) {
        Map<String, JsonNode> hostMapById = buildHostMap(hosts);

        JsonNode problemsJson = zabbixClient.getAllActiveProblems();

        List<ZabbixProblemDTO> dtos = new ArrayList<>();

        if (problemsJson == null || !problemsJson.isArray()) return dtos;

        for (JsonNode node : problemsJson) {

            String hostId = null;
            JsonNode hostRefs = node.path("hosts");
            if (hostRefs.isArray() && !hostRefs.isEmpty()) {
                hostId = hostRefs.get(0).path("hostid").asText(null);
            }
            if (hostId == null || hostId.isBlank()) {
                hostId = node.path("hostid").asText(null);
            }
            if (hostId == null || hostId.isBlank()) {
                JsonNode trigger = fetchTriggerById(node.path("objectid").asText(null));
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

            long startedAt = node.path("clock").asLong();
            long resolvedAt = node.path("r_clock").asLong();

            boolean isResolved = resolvedAt > 0;

            dtos.add(
                    ZabbixProblemDTO.builder()
                            .problemId(node.path("eventid").asText())
                            .host(fullHost != null ? fullHost.path("host").asText() : "UNKNOWN")
                            .hostId(hostId)
                            .description(node.path("name").asText())
                            .severity(node.path("severity").asText())
                            .active(!isResolved)
                            .source("Zabbix")
                            .eventId(node.path("eventid").asLong())
                            .ip(fullHost != null ? extractMainIp(fullHost) : null)
                            .port(fullHost != null ? extractMainPort(fullHost) : null)

                            //  DATE DEBUT
                            .startedAt(startedAt)
                            .startedAtFormatted(formatDate(startedAt))

                            //  DATE FIN
                            .resolvedAt(resolvedAt == 0 ? null : resolvedAt)
                            .resolvedAtFormatted(resolvedAt == 0 ? null : formatDate(resolvedAt))

                            //  STATUS
                            .status(isResolved ? "RESOLVED" : "ACTIVE")

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
        JsonNode hosts = trigger.get(0).path("hosts");
        if (!hosts.isArray() || hosts.isEmpty()) {
            return null;
        }
        return hosts.get(0).path("hostid").asText(null);
    }

    private Map<String, JsonNode> buildHostMap(JsonNode hosts) {
        Map<String, JsonNode> map = new HashMap<>();

        if (hosts != null && hosts.isArray()) {
            for (JsonNode h : hosts) {
                map.put(h.path("hostid").asText(), h);
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

        if (hosts == null || !hosts.isArray()) return metrics;

        List<String> hostIds = new ArrayList<>();
        Map<String, String> hostNames = new HashMap<>();

        for (JsonNode host : hosts) {
            String id = host.path("hostid").asText();
            hostIds.add(id);
            hostNames.put(id, host.path("host").asText());
        }

        JsonNode items = zabbixClient.getItemsByHosts(hostIds);
        if (items == null || !items.isArray()) return metrics;

        List<String> floatItems = new ArrayList<>();
        List<String> intItems = new ArrayList<>();

        Map<String, String> itemKeyMap = new HashMap<>();
        Map<String, String> itemHostMap = new HashMap<>();

        for (JsonNode item : items) {
            String key = item.path("key_").asText();

            if (!isUsefulMetric(key)) {
                continue;
            }

            String itemId = item.path("itemid").asText();
            int valueType = item.path("value_type").asInt();
            String hostId = item.path("hostid").asText();

            itemKeyMap.put(itemId, key);
            itemHostMap.put(itemId, hostId);

            if (valueType == 0) floatItems.add(itemId);
            if (valueType == 3) intItems.add(itemId);
        }

        long now = Instant.now().getEpochSecond();
        long windowStart = now - HISTORY_WINDOW_SECONDS;

        log.info("[ZABBIX] Useful items fetched: float={}, int={}", floatItems.size(), intItems.size());

        if (!floatItems.isEmpty()) {
            for (List<String> batch : chunkList(floatItems, HISTORY_BATCH_SIZE)) {
                try {
                    JsonNode history = zabbixClient.getHistoryBatch(batch, 0, windowStart, now);
                    mapHistory(metrics, history, itemKeyMap, itemHostMap, hostNames, hostMap);
                } catch (Exception e) {
                    log.error("[ZABBIX] Failed float history batch size={}, skipping batch", batch.size(), e);
                }
            }
        }

        if (!intItems.isEmpty()) {
            for (List<String> batch : chunkList(intItems, HISTORY_BATCH_SIZE)) {
                try {
                    JsonNode history = zabbixClient.getHistoryBatch(batch, 3, windowStart, now);
                    mapHistory(metrics, history, itemKeyMap, itemHostMap, hostNames, hostMap);
                } catch (Exception e) {
                    log.error("[ZABBIX] Failed int history batch size={}, skipping batch", batch.size(), e);
                }
            }
        }

        log.info("[ZABBIX] Final mapped metrics count={}", metrics.size());
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
        if (history == null || !history.isArray()) return;

        for (JsonNode point : history) {

            String itemId = point.path("itemid").asText();
            String hostId = itemHostMap.get(itemId);
            if (hostId == null) continue;
            MonitoredHost mh = hostMap.get(hostId);

            metrics.add(
                    ZabbixMetricDTO.builder()
                            .hostId(hostId)
                            .hostName(hostNames.getOrDefault(hostId, "UNKNOWN"))
                            .ip(mh != null ? mh.getIp() : null)
                            .port(mh != null ? mh.getPort() : null)
                            .itemId(itemId)
                            .metricKey(itemKeyMap.get(itemId))
                            .value(point.path("value").asText().isEmpty() ? 0.0 : point.path("value").asDouble())
                            .timestamp(point.path("clock").asLong())
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
