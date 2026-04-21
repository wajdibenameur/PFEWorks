package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.dto.ObserviumMetricDTO;
import tn.iteam.dto.ObserviumProblemDTO;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Mapper for converting Observium DTOs to unified monitoring DTOs.
 * Note: Observium API does not provide timestamp fields (startedAt/resolvedAt) for alerts.
 * We use the collection time as a fallback for startedAt.
 */
@Component
public class ObserviumMonitoringMapper {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

        Long startedAt = dto.getStartedAt();
        String startedAtFormatted = dto.getStartedAtFormatted();
        Long resolvedAt = dto.getResolvedAt();
        String resolvedAtFormatted = dto.getResolvedAtFormatted();

        if (startedAt == null) {
            startedAt = Instant.now().getEpochSecond();
        }
        if (startedAtFormatted == null) {
            startedAtFormatted = formatTimestamp(startedAt);
        }
        if (resolvedAt != null && resolvedAtFormatted == null) {
            resolvedAtFormatted = formatTimestamp(resolvedAt);
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

    private String formatTimestamp(Long epoch) {
        if (epoch == null) return null;
        try {
            return Instant.ofEpochSecond(epoch)
                    .atZone(ZoneId.systemDefault())
                    .format(DATE_TIME_FORMATTER);
        } catch (Exception e) {
            return null;
        }
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
