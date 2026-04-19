package tn.iteam.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.util.MonitoringConstants;

@Component
public class ZkBioMapper {

    private static final String ID_FIELD = "id";
    private static final String DEVICE_NAME_FIELD = "device_name";
    private static final String MESSAGE_FIELD = "message";
    private static final String SEVERITY_FIELD = "severity";
    private static final String ACTIVE_STATUS = "active";

    public ZkBioProblemDTO mapAlertToDTO(JsonNode alertNode) {
        return ZkBioProblemDTO.builder()
                .problemId(alertNode.has(ID_FIELD) ? alertNode.get(ID_FIELD).asText() : MonitoringConstants.UNKNOWN)
                .host(alertNode.has(DEVICE_NAME_FIELD) ? alertNode.get(DEVICE_NAME_FIELD).asText() : MonitoringConstants.UNKNOWN)
                .description(alertNode.has(MESSAGE_FIELD) ? alertNode.get(MESSAGE_FIELD).asText() : MonitoringConstants.NO_DESCRIPTION)
                .severity(alertNode.has(SEVERITY_FIELD) ? alertNode.get(SEVERITY_FIELD).asText() : MonitoringConstants.UNKNOWN)
                .active(alertNode.has(MonitoringConstants.STATUS_FIELD)
                        && alertNode.get(MonitoringConstants.STATUS_FIELD).asText().equalsIgnoreCase(ACTIVE_STATUS))
                .source(MonitoringConstants.SOURCE_ZKBIO)
                .eventId(alertNode.has(ID_FIELD) ? alertNode.get(ID_FIELD).asLong() : 0L)
                .build();
    }
}
