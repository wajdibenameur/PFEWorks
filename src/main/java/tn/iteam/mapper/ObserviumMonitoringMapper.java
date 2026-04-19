package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.dto.ObserviumProblemDTO;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;

import java.util.List;

/**
 * Mapper for converting Observium DTOs to unified monitoring DTOs.
 */
@Component
public class ObserviumMonitoringMapper {

    public UnifiedMonitoringHostDTO toHost(ServiceStatusDTO dto) {
        String hostName = normalizeText(dto.getName());
        String ip = normalizeIp(dto.getIp());
        String hostKey = hostName != null ? hostName : (ip != null ? ip : "UNKNOWN");

        return UnifiedMonitoringHostDTO.builder()
                .id(MonitoringSourceType.OBSERVIUM + ":" + hostKey)
                .source(MonitoringSourceType.OBSERVIUM)
                .hostId(hostKey)
                .name(hostName != null ? hostName : hostKey)
                .ip(ip)
                .port(null)
                .protocol(null)
                .status(normalizeText(dto.getStatus()))
                .category(normalizeText(dto.getCategory()))
                .build();
    }

    public UnifiedMonitoringProblemDTO toProblem(ObserviumProblemDTO dto) {
        String hostName = dto.getHost() != null && !dto.getHost().isBlank() ? dto.getHost() : "UNKNOWN";
        String hostKey = dto.getHostId() != null && !dto.getHostId().isBlank() ? dto.getHostId() : hostName;

        return UnifiedMonitoringProblemDTO.builder()
                .id(MonitoringSourceType.OBSERVIUM + ":" + dto.getProblemId())
                .source(MonitoringSourceType.OBSERVIUM)
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

    public List<UnifiedMonitoringHostDTO> toHosts(List<ServiceStatusDTO> dtos) {
        return dtos.stream()
                .map(this::toHost)
                .toList();
    }

    public List<UnifiedMonitoringProblemDTO> toProblems(List<ObserviumProblemDTO> dtos) {
        return dtos.stream()
                .map(this::toProblem)
                .toList();
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank() || "UNKNOWN".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    private String normalizeIp(String value) {
        if (value == null || value.isBlank() || "IP_UNKNOWN".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }
}