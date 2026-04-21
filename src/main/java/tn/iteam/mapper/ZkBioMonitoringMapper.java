package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZkBioMetricDTO;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Mapper for converting ZKBio DTOs to unified monitoring DTOs.
 * Note: ZKBio API does not provide timestamp fields (startedAt/resolvedAt) for alerts.
 * We use the collection time as a fallback for startedAt.
 */
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
        Long startedAt = dto.getStartedAt() != null ? dto.getStartedAt() : Instant.now().getEpochSecond();
        String startedAtFormatted = dto.getStartedAtFormatted() != null
                ? dto.getStartedAtFormatted()
                : formatTimestamp(startedAt);
        String status = dto.getStatus() != null ? dto.getStatus() : (dto.isActive() ? "ACTIVE" : "RESOLVED");

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
                .status(status)
                .startedAt(startedAt)
                .startedAtFormatted(startedAtFormatted)
                .resolvedAt(dto.getResolvedAt())
                .resolvedAtFormatted(dto.getResolvedAtFormatted())
                .build();
    }

    public UnifiedMonitoringMetricDTO toMetric(ZkBioMetricDTO dto) {
        return UnifiedMonitoringMetricDTO.builder()
                .id(MonitoringSourceType.ZKBIO + ":" + dto.getHostId() + ":" + dto.getItemId() + ":" + dto.getTimestamp())
                .source(MonitoringSourceType.ZKBIO)
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

    private String formatTimestamp(Long epoch) {
        if (epoch == null) return null;
        try {
            return Instant.ofEpochSecond(epoch)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank() || "UNKNOWN".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }
}
