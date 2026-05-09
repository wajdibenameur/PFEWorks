package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.dto.ObserviumMetricDTO;
import tn.iteam.dto.ObserviumProblemDTO;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.util.MonitoringConstants;
import tn.iteam.util.MonitoringNormalizeUtils;

import java.time.Instant;
import java.util.List;

/**
 * Mapper for converting Observium DTOs to unified monitoring DTOs.
 * Note: Observium API does not provide timestamp fields (startedAt/resolvedAt) for alerts.
 * We use the collection time as a fallback for startedAt.
 */
@Component
public class ObserviumMonitoringMapper {

    public UnifiedMonitoringHostDTO toHost(ServiceStatusDTO dto) {
        String hostName = MonitoringNormalizeUtils.normalizeText(dto.getName(), MonitoringConstants.UNKNOWN);
        String ip = MonitoringNormalizeUtils.normalizeIp(dto.getIp());
        String hostKey = hostName != null ? hostName : (ip != null ? ip : "UNKNOWN");

        return UnifiedMonitoringHostDTO.builder()
                .id(MonitoringSourceType.OBSERVIUM + ":" + hostKey)
                .source(MonitoringSourceType.OBSERVIUM)
                .hostId(hostKey)
                .name(hostName != null ? hostName : hostKey)
                .ip(ip)
                .port(null)
                .protocol(null)
                .status(MonitoringNormalizeUtils.normalizeText(dto.getStatus(), MonitoringConstants.UNKNOWN))
                .category(MonitoringNormalizeUtils.normalizeText(dto.getCategory(), MonitoringConstants.UNKNOWN))
                .build();
    }

    public UnifiedMonitoringProblemDTO toProblem(ObserviumProblemDTO dto) {
        String hostName = dto.getHost() != null && !dto.getHost().isBlank() ? dto.getHost() : "UNKNOWN";
        String hostKey = dto.getHostId() != null && !dto.getHostId().isBlank() ? dto.getHostId() : hostName;

        Long startedAt = dto.getStartedAt();
        String startedAtFormatted = dto.getStartedAtFormatted();
        Long resolvedAt = dto.getResolvedAt();
        String resolvedAtFormatted = dto.getResolvedAtFormatted();

        if (startedAt == null) {
            startedAt = Instant.now().getEpochSecond();
        }
        if (startedAtFormatted == null) {
            startedAtFormatted = MonitoringNormalizeUtils.formatTimestamp(startedAt);
        }
        if (resolvedAt != null && resolvedAtFormatted == null) {
            resolvedAtFormatted = MonitoringNormalizeUtils.formatTimestamp(resolvedAt);
        }

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
                .startedAt(startedAt)
                .startedAtFormatted(startedAtFormatted)
                .resolvedAt(resolvedAt)
                .resolvedAtFormatted(resolvedAtFormatted)
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

    public UnifiedMonitoringMetricDTO toMetric(ObserviumMetricDTO dto) {
        return UnifiedMonitoringMetricDTO.builder()
                .id(MonitoringSourceType.OBSERVIUM + ":" + dto.getHostId() + ":" + dto.getItemId() + ":" + dto.getTimestamp())
                .source(MonitoringSourceType.OBSERVIUM)
                .hostId(dto.getHostId())
                .hostName(dto.getHostName())
                .itemId(dto.getItemId())
                .metricKey(dto.getMetricKey())
                .value(dto.getValue())
                .timestamp(dto.getTimestamp())
                .ip(dto.getIp())
                .port(dto.getPort())
                .build();
    }

}
