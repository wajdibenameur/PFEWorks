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

@Component
@RequiredArgsConstructor
public class ZabbixAdapter {

    private static final Logger log = LoggerFactory.getLogger(ZabbixAdapter.class);

    private final ZabbixSyncService syncService;
    private final ZabbixClient zabbixClient;

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

        JsonNode hosts = zabbixClient.getHosts();
        Map<String, JsonNode> hostMapById = buildHostMap(hosts);

        JsonNode problemsJson = zabbixClient.getAllActiveProblems();

        List<ZabbixProblemDTO> dtos = new ArrayList<>();

        if (problemsJson == null || !problemsJson.isArray()) return dtos;

        for (JsonNode node : problemsJson) {

            JsonNode hostRefs = node.path("hosts");
            if (!hostRefs.isArray() || hostRefs.isEmpty()) continue;

            String hostId = hostRefs.get(0).path("hostid").asText();

            JsonNode fullHost = hostMapById.get(hostId);

            if (fullHost == null) continue;

            dtos.add(
                    ZabbixProblemDTO.builder()
                            .problemId(node.path("eventid").asText())
                            .host(fullHost.path("host").asText())
                            .description(node.path("name").asText())
                            .severity(node.path("severity").asText())
                            .active(true)
                            .source("Zabbix")
                            .eventId(node.path("eventid").asLong())
                            .ip(extractMainIp(fullHost))
                            .port(extractMainPort(fullHost)) //  FIX
                            .build()
            );
        }

        return dtos;
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

        Map<String, MonitoredHost> hostMap = syncService.loadHostMap();

        List<ZabbixMetricDTO> metrics = new ArrayList<>();
        JsonNode hosts = zabbixClient.getHosts();

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
            String itemId = item.path("itemid").asText();
            int valueType = item.path("value_type").asInt();
            String hostId = item.path("hostid").asText();

            itemKeyMap.put(itemId, key);
            itemHostMap.put(itemId, hostId);

            if (valueType == 0) floatItems.add(itemId);
            if (valueType == 3) intItems.add(itemId);
        }

        long now = Instant.now().getEpochSecond();
        long fiveMinutesAgo = now - 300;

        if (!floatItems.isEmpty()) {
            JsonNode history = zabbixClient.getHistoryBatch(floatItems, 0, fiveMinutesAgo, now);
            mapHistory(metrics, history, itemKeyMap, itemHostMap, hostNames, hostMap);
        }

        if (!intItems.isEmpty()) {
            JsonNode history = zabbixClient.getHistoryBatch(intItems, 3, fiveMinutesAgo, now);
            mapHistory(metrics, history, itemKeyMap, itemHostMap, hostNames, hostMap);
        }

        return metrics;
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
                            .itemId(itemId)
                            .metricKey(itemKeyMap.get(itemId))
                            .value(point.path("value").asText().isEmpty() ? 0.0 : point.path("value").asDouble())
                            .timestamp(point.path("clock").asLong())
                            .build()
            );
        }
    }
}