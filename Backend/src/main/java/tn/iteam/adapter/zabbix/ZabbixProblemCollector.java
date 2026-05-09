package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tn.iteam.adapter.zabbix.ZabbixClient;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.util.MonitoringConstants;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * Collector dedicated to Zabbix problem collection.
 * Handles problems retrieval, trigger resolution, and mapping to DTOs.
 */
@Component
public class ZabbixProblemCollector {

    private static final Logger log = LoggerFactory.getLogger(ZabbixProblemCollector.class);
    private static final String LOG_PREFIX = "[ZABBIX-PROBLEM-COLLECTOR] ";
    private static final int PROBLEM_TRIGGER_BATCH_SIZE = 25;
    private static final int RESOLVED_AT_ZERO = 0;
    private static final String HOSTS_FIELD = "hosts";
    private static final String OBJECT_ID_FIELD = "objectid";
    private static final String RESOLVED_CLOCK_FIELD = "r_clock";
    private static final String SEVERITY_FIELD = "severity";
    private static final String ITEMS_FIELD = "items";
    private static final String RECEIVED_PROBLEMS_LOG_TEMPLATE = LOG_PREFIX + "Received {} problems from Zabbix API";
    private static final String MISSING_CLOCK_LOG_TEMPLATE = "Problem event {} missing clock from Zabbix payload";
    private static final String SKIPPED_PROBLEM_NO_HOST_TEMPLATE = LOG_PREFIX + "Skipped problem eventId={} because hostId is null";
    private static final String UNKNOWN_SOURCE_LABEL = MonitoringConstants.SOURCE_LABEL_ZABBIX;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ZabbixClient zabbixClient;

    public ZabbixProblemCollector(ZabbixClient zabbixClient) {
        this.zabbixClient = zabbixClient;
    }

    /**
     * Fetch all problems from Zabbix using current hosts.
     *
     * @return list of ZabbixProblemDTO
     */
    public List<ZabbixProblemDTO> fetchProblems() {
        return fetchProblems(await(zabbixClient.getHosts()));
    }

    /**
     * Fetch problems with pre-resolved hosts map.
     *
     * @param hosts JsonNode containing hosts array
     * @return list of ZabbixProblemDTO
     */
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

    /**
     * Preload triggers for problems that don't have direct host references.
     *
     * @param problemsJson JsonNode containing problems array
     * @return map of triggerId to trigger JsonNode
     */
    public Map<String, JsonNode> preloadTriggersForProblems(JsonNode problemsJson) {
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

    /**
     * Resolve the host ID for a problem from multiple possible sources.
     *
     * @param node JsonNode problem
     * @param hostMapById map of hostId to host JsonNode
     * @param triggersById map of triggerId to trigger JsonNode
     * @return resolved host ID or null
     */
    public String resolveProblemHostId(JsonNode node, Map<String, JsonNode> hostMapById, Map<String, JsonNode> triggersById) {
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

    /**
     * Extract host ID directly from problem node.
     *
     * @param node JsonNode problem
     * @return host ID or null
     */
    public String extractHostIdFromProblemNode(JsonNode node) {
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

    /**
     * Extract host ID from trigger node.
     *
     * @param trigger JsonNode trigger
     * @return host ID or null
     */
    public String extractHostIdFromTrigger(JsonNode trigger) {
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

    /**
     * Build a map of hostId -> JsonNode for quick lookup.
     *
     * @param hosts JsonNode containing hosts array
     * @return map of hostId to JsonNode
     */
    public Map<String, JsonNode> buildHostMap(JsonNode hosts) {
        Map<String, JsonNode> map = new HashMap<>();

        if (hosts != null && hosts.isArray()) {
            for (JsonNode h : hosts) {
                map.put(h.path(MonitoringConstants.HOST_ID_FIELD).asText(), h);
            }
        }

        return map;
    }

    private String extractMainIp(JsonNode hostNode) {
        for (JsonNode iface : hostNode.path("interfaces")) {
            if (iface.path(MonitoringConstants.MAIN_FIELD).asInt(0) == 1) {
                return iface.path(MonitoringConstants.IP_FIELD).asText(MonitoringConstants.IP_UNKNOWN);
            }
        }
        return MonitoringConstants.IP_UNKNOWN;
    }

    private Integer extractMainPort(JsonNode hostNode) {
        for (JsonNode iface : hostNode.path("interfaces")) {
            if (iface.path(MonitoringConstants.MAIN_FIELD).asInt(0) == 1) {
                return iface.path(MonitoringConstants.PORT_FIELD).asInt(10050);
            }
        }
        return 10050;
    }

    private String formatDate(long epoch) {
        return Instant.ofEpochSecond(epoch)
                .atZone(ZoneId.systemDefault())
                .format(DATE_TIME_FORMATTER);
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