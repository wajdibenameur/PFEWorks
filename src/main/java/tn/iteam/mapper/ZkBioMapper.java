package tn.iteam.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import tn.iteam.dto.ZkBioProblemDTO;

@Component
public class ZkBioMapper {

    public ZkBioProblemDTO mapAlertToDTO(JsonNode alertNode) {
        return ZkBioProblemDTO.builder()
                .problemId(alertNode.has("id") ? alertNode.get("id").asText() : "UNKNOWN")
                .host(alertNode.has("device_name") ? alertNode.get("device_name").asText() : "UNKNOWN")
                .description(alertNode.has("message") ? alertNode.get("message").asText() : "No description")
                .severity(alertNode.has("severity") ? alertNode.get("severity").asText() : "UNKNOWN")
                .active(alertNode.has("status") && alertNode.get("status").asText().equalsIgnoreCase("active"))
                .source("ZKBIO")
                .eventId(alertNode.has("id") ? alertNode.get("id").asLong() : 0L)
                .build();
    }
}