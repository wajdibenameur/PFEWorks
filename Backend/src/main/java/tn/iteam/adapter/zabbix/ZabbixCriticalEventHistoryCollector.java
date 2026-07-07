package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tn.iteam.domain.MonitoredHost;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.repository.MonitoredHostRepository;
import tn.iteam.util.MonitoringConstants;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

@Component
public class ZabbixCriticalEventHistoryCollector {

    private static final Logger log = LoggerFactory.getLogger(ZabbixCriticalEventHistoryCollector.class);
    private static final String LOG_PREFIX = "[ZABBIX-CRITICAL-HISTORY] ";
    private static final String HOSTS_FIELD = "hosts";
    private static final String RELATED_OBJECT_FIELD = "relatedObject";
    private static final String DESCRIPTION_FIELD = "description";
    private static final String NAME_FIELD = "name";
    private static final String EVENT_ID_FIELD = "eventid";
    private static final String CLOCK_FIELD = "clock";
    private static final String RCLOCK_FIELD = "r_clock";
    private static final String REVENT_ID_FIELD = "r_eventid";
    private static final String SEVERITY_FIELD = "severity";

    private final ZabbixClient zabbixClient;
    private final MonitoredHostRepository monitoredHostRepository;

    @Value("${zabbix.critical-history.lookback-days:730}")
    private long lookbackDays;

    @Value("${zabbix.critical-history.limit:5000}")
    private int limit;

    public ZabbixCriticalEventHistoryCollector(
            ZabbixClient zabbixClient,
            MonitoredHostRepository monitoredHostRepository
    ) {
        this.zabbixClient = zabbixClient;
        this.monitoredHostRepository = monitoredHostRepository;
    }

    public List<ZabbixProblemDTO> collectCriticalHistory() {
        long timeTill = Instant.now().getEpochSecond();
        long effectiveLookbackDays = Math.max(1L, lookbackDays);
        long timeFrom = Instant.now().minus(effectiveLookbackDays, ChronoUnit.DAYS).getEpochSecond();

        JsonNode events = await(zabbixClient.getCriticalEventsHistory(timeFrom, timeTill));
        if (events == null || !events.isArray() || events.isEmpty()) {
            return List.of();
        }

        Map<String, MonitoredHost> monitoredHostsById = new HashMap<>();
        for (MonitoredHost host : monitoredHostRepository.findBySourceOrderByNameAsc(MonitoringConstants.SOURCE_ZABBIX)) {
            if (host.getHostId() != null && !host.getHostId().isBlank()) {
                monitoredHostsById.put(host.getHostId(), host);
            }
        }

        List<ZabbixProblemDTO> dtos = new ArrayList<>();
        int effectiveLimit = limit > 0 ? Math.min(limit, events.size()) : events.size();
        for (int i = 0; i < events.size() && dtos.size() < effectiveLimit; i++) {
            ZabbixProblemDTO dto = mapEvent(events.get(i), monitoredHostsById);
            if (dto != null) {
                dtos.add(dto);
            }
        }
        log.info(LOG_PREFIX + "Collected {} critical historical events after filtering", dtos.size());
        return List.copyOf(dtos);
    }

    ZabbixProblemDTO mapEvent(JsonNode eventNode, Map<String, MonitoredHost> monitoredHostsById) {
        if (eventNode == null || eventNode.isMissingNode() || eventNode.isNull()) {
            return null;
        }

        String severity = eventNode.path(SEVERITY_FIELD).asText(null);
        if (!"4".equals(severity) && !"5".equals(severity)) {
            return null;
        }

        String eventIdRaw = eventNode.path(EVENT_ID_FIELD).asText(null);
        Long eventId = parseLong(eventIdRaw);
        Long startedAt = parseLong(eventNode.path(CLOCK_FIELD).asText(null));
        if (eventId == null || startedAt == null) {
            return null;
        }

        JsonNode hostNode = eventNode.path(HOSTS_FIELD).isArray() && !eventNode.path(HOSTS_FIELD).isEmpty()
                ? eventNode.path(HOSTS_FIELD).get(0)
                : null;
        String hostId = hostNode != null ? hostNode.path(MonitoringConstants.HOST_ID_FIELD).asText(null) : null;
        if (hostId == null || hostId.isBlank()) {
            return null;
        }

        MonitoredHost monitoredHost = resolveMonitoredHost(hostId, monitoredHostsById);
        String hostName = hostNode != null ? hostNode.path(MonitoringConstants.HOST_FIELD).asText(null) : null;
        if ((hostName == null || hostName.isBlank()) && monitoredHost != null) {
            hostName = monitoredHost.getName();
        }

        String description = resolveDescription(eventNode);
        if (description == null || description.isBlank()) {
            return null;
        }

        Long resolvedAt = parseLong(eventNode.path(RCLOCK_FIELD).asText(null));
        boolean active = !hasResolutionMarker(eventNode, resolvedAt);

        return ZabbixProblemDTO.builder()
                .problemId(eventIdRaw)
                .eventId(eventId)
                .hostId(hostId)
                .host(hostName != null && !hostName.isBlank() ? hostName : hostId)
                .description(description)
                .severity(severity)
                .active(active)
                .ip(monitoredHost != null ? monitoredHost.getIp() : null)
                .port(monitoredHost != null ? monitoredHost.getPort() : null)
                .startedAt(startedAt)
                .resolvedAt(active ? null : resolvedAt)
                .source(MonitoringConstants.SOURCE_LABEL_ZABBIX)
                .status(active ? MonitoringConstants.STATUS_ACTIVE : MonitoringConstants.STATUS_RESOLVED)
                .build();
    }

    private MonitoredHost resolveMonitoredHost(String hostId, Map<String, MonitoredHost> monitoredHostsById) {
        MonitoredHost monitoredHost = monitoredHostsById.get(hostId);
        if (monitoredHost != null) {
            return monitoredHost;
        }

        JsonNode hostById = await(zabbixClient.getHostById(hostId));
        if (hostById == null || !hostById.isArray() || hostById.isEmpty()) {
            return null;
        }

        JsonNode hostNode = hostById.get(0);
        MonitoredHost resolved = MonitoredHost.builder()
                .hostId(hostId)
                .name(hostNode.path(MonitoringConstants.HOST_FIELD).asText(hostId))
                .ip(extractMainIp(hostNode))
                .port(extractMainPort(hostNode))
                .source(MonitoringConstants.SOURCE_ZABBIX)
                .build();
        monitoredHostsById.put(hostId, resolved);
        return resolved;
    }

    private String resolveDescription(JsonNode eventNode) {
        String directName = eventNode.path(NAME_FIELD).asText(null);
        if (directName != null && !directName.isBlank()) {
            return directName;
        }

        JsonNode relatedObject = eventNode.path(RELATED_OBJECT_FIELD);
        String relatedDescription = relatedObject.path(DESCRIPTION_FIELD).asText(null);
        if (relatedDescription != null && !relatedDescription.isBlank()) {
            return relatedDescription;
        }

        String relatedName = relatedObject.path(NAME_FIELD).asText(null);
        if (relatedName != null && !relatedName.isBlank()) {
            return relatedName;
        }
        return null;
    }

    private boolean hasResolutionMarker(JsonNode eventNode, Long resolvedAt) {
        if (resolvedAt != null && resolvedAt > 0) {
            return true;
        }
        String recoveryEventId = eventNode.path(REVENT_ID_FIELD).asText(null);
        return recoveryEventId != null && !recoveryEventId.isBlank() && !"0".equals(recoveryEventId);
    }

    private String extractMainIp(JsonNode hostNode) {
        for (JsonNode iface : hostNode.path("interfaces")) {
            if (iface.path(MonitoringConstants.MAIN_FIELD).asInt(0) == 1) {
                return iface.path(MonitoringConstants.IP_FIELD).asText(null);
            }
        }
        return null;
    }

    private Integer extractMainPort(JsonNode hostNode) {
        for (JsonNode iface : hostNode.path("interfaces")) {
            if (iface.path(MonitoringConstants.MAIN_FIELD).asInt(0) == 1) {
                return iface.path(MonitoringConstants.PORT_FIELD).isMissingNode()
                        ? null
                        : iface.path(MonitoringConstants.PORT_FIELD).asInt();
            }
        }
        return null;
    }

    private Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private <T> T await(reactor.core.publisher.Mono<T> mono) {
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
}
