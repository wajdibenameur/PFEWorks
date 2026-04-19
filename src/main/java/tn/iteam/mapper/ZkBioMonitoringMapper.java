package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;

@Component
public class ZkBioMonitoringMapper {

    public UnifiedMonitoringHostDTO toHost(ServiceStatusDTO dto) {
        String hostName = normalizeText(dto.getName());
        String ip = normalizeText(dto.getIp());
        String hostKey = hostName != null ? hostName : (ip != null ? ip : "UNKNOWN");

        return UnifiedMonitoringHostDTO.builder()
                .id(MonitoringSourceType.ZKBIO + ":" + hostKey)
                .source(MonitoringSourceType.ZKBIO)
                .hostId(hostKey)
                .name(hostName != null ? hostName : hostKey)
                .ip(ip)
                .port(dto.getPort())
                .protocol(normalizeText(dto.getProtocol()))
                .status(normalizeText(dto.getStatus()))
                .category(normalizeText(dto.getCategory()))
                .build();
    }

    public UnifiedMonitoringProblemDTO toProblem(ZkBioProblemDTO dto) {
        String hostName = normalizeText(dto.getHost());
        String hostKey = hostName != null ? hostName : "UNKNOWN";

        return UnifiedMonitoringProblemDTO.builder()
                .id(MonitoringSourceType.ZKBIO + ":" + dto.getProblemId())
                .source(MonitoringSourceType.ZKBIO)
                .problemId(dto.getProblemId())
                .eventId(dto.getEventId())
                .hostId(hostKey)
                .hostName(hostName)
                .description(dto.getDescription())
                .severity(dto.getSeverity())
                .active(dto.isActive())
                .status(dto.isActive() ? "ACTIVE" : "RESOLVED")
                .build();
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank() || "UNKNOWN".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }
}
