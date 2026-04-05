package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.iteam.domain.MonitoredHost;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.service.ZabbixSyncService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ZabbixAdapter {

    private static final Logger log = LoggerFactory.getLogger(ZabbixAdapter.class);
    private final ZabbixSyncService syncService;
    private final ZabbixClient zabbixClient;

    // ================== HOSTS ==================
    public List<ServiceStatusDTO> fetchAll() {
        log.info(" Fetching hosts from Zabbix");

        Map<String, MonitoredHost> hostMap = syncService.loadHostMap();
        log.info("Hosts Map loaded: {}", hostMap); //  AJOUT

        JsonNode hosts = zabbixClient.getHosts();
        List<ServiceStatusDTO> dtos = new ArrayList<>();

        if (hosts == null || !hosts.isArray() || hosts.isEmpty()) {
            log.warn("No hosts received from Zabbix");
            return dtos;
        }

        for (JsonNode hostNode : hosts) {
            ServiceStatusDTO dto = new ServiceStatusDTO();
            dto.setSource("ZABBIX");
            dto.setName(hostNode.path("host").asText("UNKNOWN"));
            dto.setIp(extractMainIp(hostNode));
            dto.setPort(80);
            dto.setProtocol("HTTP");
            dto.setStatus(hostNode.path("status").asInt(1) == 0 ? "UP" : "DOWN");
            dto.setCategory("SERVER");

            dtos.add(dto);
        }

        log.info(" {} hosts fetched from Zabbix", dtos.size());
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

    // ================== PROBLEMS ==================
    public List<ZabbixProblemDTO> fetchProblems() {
        log.info(" Fetching problems from Zabbix");

        Map<String, MonitoredHost> hostMap = syncService.loadHostMap();
        log.info("Hosts Map loaded: {}", hostMap); //  AJOUT

        JsonNode problemsJson = zabbixClient.getAllActiveProblems();
        List<ZabbixProblemDTO> dtos = new ArrayList<>();

        if (problemsJson == null || !problemsJson.isArray() || problemsJson.isEmpty()) {
            log.warn("No problems received or invalid response");
            return dtos;
        }

        for (JsonNode node : problemsJson) {
            log.debug("Processing problem node: {}", node.toPrettyString()); //  AJOUT
            try {
                ZabbixProblemDTO dto = mapToDTO(node, hostMap);
                if (dto != null) {
                    dtos.add(dto);
                    log.debug("Mapped DTO: {}", dto); //  AJOUT
                } else {
                    log.warn("Skipped problem node (no host found): {}", node.toPrettyString());
                }
            } catch (Exception e) {
                log.error("Error mapping problem node to DTO: {}", node.toPrettyString(), e);
            }
        }

        log.info(" {} problems fetched from Zabbix", dtos.size());
        return dtos;
    }

    private ZabbixProblemDTO mapToDTO(JsonNode node, Map<String, MonitoredHost> hostMap) {
        JsonNode hosts = node.path("hosts");
        if (hosts.isEmpty()) {
            log.warn("Problem node has no hosts: {}", node.toPrettyString());
            return null;
        }

        JsonNode hostNode = hosts.get(0);
        String hostId = hostNode.path("hostid").asText(null);
        String hostName = hostNode.path("host").asText("UNKNOWN");
        String ip = null;

        if (hostId != null && hostMap.containsKey(hostId)) {
            ip = hostMap.get(hostId).getIp();
        } else {
            log.warn("HostId {} not found in hostMap", hostId);
        }

        return ZabbixProblemDTO.builder()
                .problemId(node.path("eventid").asText("UNKNOWN"))
                .host(hostName)
                .description(node.path("name").asText("No description"))
                .severity(node.path("severity").asText("UNKNOWN"))
                .active(true)
                .source("Zabbix")
                .eventId(node.path("eventid").asLong(0))
                .ip(ip)
                .build();
    }

    // ================== METRICS ==================
    public List<ZabbixMetric> fetchMetricsAndMap() {

        log.info("===== ZABBIX METRICS COLLECTION START =====");

        Map<String, MonitoredHost> hostMap = syncService.loadHostMap();
        log.info("Hosts Map loaded: {}", hostMap); //  AJOUT

        List<ZabbixMetric> metrics = new ArrayList<>();
        JsonNode hosts = zabbixClient.getHosts();
        if (hosts == null || !hosts.isArray()) return metrics;

        List<String> hostIds = new ArrayList<>();
        Map<String, String> hostNames = new HashMap<>();

        for (JsonNode host : hosts) {
            String id = host.path("hostid").asText();
            hostIds.add(id);
            hostNames.put(id, host.path("host").asText());
        }

        // UN SEUL item.get pour tous les hosts
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

            if (!key.contains("cpu") && !key.contains("memory") && !key.contains("net")) continue;

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

        log.info("Collected {} metrics", metrics.size());
        return metrics;
    }

    private void mapHistory(
            List<ZabbixMetric> metrics,
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
            MonitoredHost mh = hostMap.get(hostId);

            metrics.add(
                    ZabbixMetric.builder()
                            .hostId(hostId)
                            .hostName(hostNames.get(hostId))
                            .ip(mh != null ? mh.getIp() : null)
                            .itemId(itemId)
                            .metricKey(itemKeyMap.get(itemId))
                            .value(point.path("value").asDouble())
                            .timestamp(point.path("clock").asLong())
                            .build()
            );
        }
    }
}
