package tn.iteam.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import tn.iteam.dto.ObserviumProblemDTO;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZabbixProblemDTO;

@Component
public class ObserviumMapper {

    /**
     * Transforme un device JSON Observium en ServiceStatusDTO
     */
    public ServiceStatusDTO mapDeviceToDTO(JsonNode deviceNode) {
        ServiceStatusDTO dto = new ServiceStatusDTO();
        dto.setSource("OBSERVIUM");

        dto.setName(deviceNode.has("hostname") ? deviceNode.get("hostname").asText() : "UNKNOWN");
        dto.setIp(deviceNode.has("ip") ? deviceNode.get("ip").asText() : "IP_UNKNOWN");

        dto.setPort(80);          // Par défaut, HTTP
        dto.setProtocol("HTTP");
        dto.setCategory("SERVER");

        // Statut selon la présence de "status" ou "ping"
        // Observium fournit souvent "status" = 0/1 (0 = up, 1 = down)
        if (deviceNode.has("status")) {
            dto.setStatus(deviceNode.get("status").asInt() == 0 ? "UP" : "DOWN");
        } else {
            dto.setStatus("UNKNOWN");
        }

        return dto;
    }
    /**
     * Transforme une alert Observium en ZabbixProblemDTO
     */
    public ObserviumProblemDTO mapAlertToDTO(JsonNode alertNode) {
        return ObserviumProblemDTO.builder()
                .problemId(alertNode.has("alert_id") ? alertNode.get("alert_id").asText() : "UNKNOWN")
                .host(alertNode.has("hostname") ? alertNode.get("hostname").asText() : "UNKNOWN")
                .description(alertNode.has("message") ? alertNode.get("message").asText() : "No description")
                .severity(alertNode.has("severity") ? alertNode.get("severity").asText() : "UNKNOWN")
                .active(alertNode.has("status") && alertNode.get("status").asText().equalsIgnoreCase("open"))
                .source("OBSERVIUM")
                .eventId(alertNode.has("alert_id") ? alertNode.get("alert_id").asLong() : 0L)
                .build();
    }
}