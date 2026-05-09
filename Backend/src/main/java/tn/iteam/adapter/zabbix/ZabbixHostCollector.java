package tn.iteam.adapter.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tn.iteam.adapter.zabbix.ZabbixClient;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.util.MonitoringConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

/**
 * Collector dedicated to Zabbix host collection.
 * Handles hosts retrieval and mapping to DTOs.
 */
@Component
public class ZabbixHostCollector {

    private static final Logger log = LoggerFactory.getLogger(ZabbixHostCollector.class);
    private static final String LOG_PREFIX = "[ZABBIX-HOST-COLLECTOR] ";
    private static final String INTERFACES_FIELD = "interfaces";
    private static final int DEFAULT_MAIN_INTERFACE_FLAG = 1;
    private static final int DEFAULT_HOST_STATUS = 1;
    private static final int DEFAULT_ZABBIX_PORT = 10050;

    private final ZabbixClient zabbixClient;

    public ZabbixHostCollector(ZabbixClient zabbixClient) {
        this.zabbixClient = zabbixClient;
    }

    /**
     * Fetch all hosts from Zabbix and map to DTOs.
     *
     * @return list of ServiceStatusDTO
     */
    public List<ServiceStatusDTO> fetchAll() {
        return fetchAll(fetchHosts());
    }

    /**
     * Map already-fetched hosts to DTOs.
     *
     * @param hosts JsonNode containing hosts array
     * @return list of ServiceStatusDTO
     */
    public List<ServiceStatusDTO> fetchAll(JsonNode hosts) {
        return mapHostsToDto(hosts);
    }

    /**
     * Fetch raw hosts payload from Zabbix.
     *
     * @return JsonNode containing hosts array
     */
    public JsonNode fetchHosts() {
        return await(zabbixClient.getHosts());
    }

    /**
     * Map JsonNode hosts to ServiceStatusDTO list.
     *
     * @param hosts JsonNode containing hosts array
     * @return list of ServiceStatusDTO
     */
    public List<ServiceStatusDTO> mapHostsToDto(JsonNode hosts) {
        List<ServiceStatusDTO> dtos = new ArrayList<>();

        if (hosts == null || !hosts.isArray()) {
            return dtos;
        }

        log.info(LOG_PREFIX + "Received {} hosts from Zabbix", hosts.size());

        for (JsonNode hostNode : hosts) {
            ServiceStatusDTO dto = new ServiceStatusDTO();
            dto.setSource(MonitoringConstants.SOURCE_ZABBIX);
            dto.setHostId(hostNode.path(MonitoringConstants.HOST_ID_FIELD).asText(null));
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

    /**
     * Build a map of hostId -> JsonNode for quick lookup.
     *
     * @param hosts JsonNode containing hosts array
     * @return map of hostId to JsonNode
     */
    public java.util.Map<String, JsonNode> buildHostMap(JsonNode hosts) {
        java.util.Map<String, JsonNode> map = new java.util.HashMap<>();

        if (hosts != null && hosts.isArray()) {
            for (JsonNode h : hosts) {
                map.put(h.path(MonitoringConstants.HOST_ID_FIELD).asText(), h);
            }
        }

        return map;
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
}
