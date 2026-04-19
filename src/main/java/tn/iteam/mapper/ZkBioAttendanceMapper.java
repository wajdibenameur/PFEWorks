package tn.iteam.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import tn.iteam.dto.ZkBioAttendanceDTO;

/**
 * Mapper pour les journaux de présence ZKBio
 */
@Component
public class ZkBioAttendanceMapper {

    public ZkBioAttendanceDTO mapToDTO(JsonNode logNode) {
        return ZkBioAttendanceDTO.builder()
                // User info
                .userId(getTextOrDefault(logNode, "user_id", "user_id", "UNKNOWN"))
                .userName(getTextOrDefault(logNode, "user_name", "name", "Unknown User"))
                
                // Device info
                .deviceId(getTextOrDefault(logNode, "device_id", "sn", "UNKNOWN"))
                .deviceName(getTextOrDefault(logNode, "device_name", "device_name", "Unknown Device"))
                
                // Timestamp
                .timestamp(getLongOrDefault(logNode, "timestamp", "clock"))
                
                // Verification type
                .verifyType(getTextOrDefault(logNode, "verify_type", "verifyType", "UNKNOWN"))
                
                // In/Out mode
                .inOutMode(getTextOrDefault(logNode, "in_out_mode", "inOutMode", "CheckIn"))
                
                // Status
                .status(getTextOrDefault(logNode, "status", "status", "Normal"))
                
                // Event type
                .eventType(getTextOrDefault(logNode, "event_type", "eventType", "Attendance"))
                
                .source("ZKBIO")
                .build();
    }

    private String getTextOrDefault(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && !node.get(fieldName).isNull()) {
                return node.get(fieldName).asText();
            }
        }
        return "UNKNOWN";
    }

    private Long getLongOrDefault(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName) && !node.get(fieldName).isNull()) {
                return node.get(fieldName).asLong();
            }
        }
        return 0L;
    }
}