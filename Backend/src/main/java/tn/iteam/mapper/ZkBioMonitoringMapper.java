package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZkBioMetricDTO;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.util.MonitoringConstants;
import tn.iteam.util.MonitoringNormalizeUtils;

import java.time.Instant;

/**
 * Mapper for converting ZKBio DTOs to unified monitoring DTOs.
 * Note: ZKBio API does not provide timestamp fields (startedAt/resolvedAt) for alerts.
 * We use the collection time as a fallback for startedAt.
 */
@Component
public class ZkBioMonitoringMapper {

    public UnifiedMonitoringHostDTO toHost(ServiceStatusDTO dto) {
        String hostName = MonitoringNormalizeUtils.normalizeText(dto.getName(), MonitoringConstants.UNKNOWN);
        String ip = MonitoringNormalizeUtils.normalizeText(dto.getIp(), MonitoringConstants.UNKNOWN);
        String hostKey = hostName != null ? hostName : (ip != null ? ip : "UNKNOWN");

        return UnifiedMonitoringHostDTO.builder()
                .id(MonitoringSourceType.ZKBIO + ":" + hostKey)
                .source(MonitoringSourceType.ZKBIO)
                .hostId(hostKey)
                .name(hostName != null ? hostName : hostKey)
                .ip(ip)
                .port(dto.getPort())
                .protocol(MonitoringNormalizeUtils.normalizeText(dto.getProtocol(), MonitoringConstants.UNKNOWN))
                .status(MonitoringNormalizeUtils.normalizeText(dto.getStatus(), MonitoringConstants.UNKNOWN))
                .category(MonitoringNormalizeUtils.normalizeText(dto.getCategory(), MonitoringConstants.UNKNOWN))
                .build();
    }

    public UnifiedMonitoringProblemDTO toProblem(ZkBioProblemDTO dto) {
        String hostName = MonitoringNormalizeUtils.normalizeText(dto.getHost(), MonitoringConstants.UNKNOWN);
        String hostKey = hostName != null ? hostName : "UNKNOWN";
        Long startedAt = dto.getStartedAt() != null ? dto.getStartedAt() : Instant.now().getEpochSecond();
        String startedAtFormatted = dto.getStartedAtFormatted() != null
                ? dto.getStartedAtFormatted()
                : MonitoringNormalizeUtils.formatTimestamp(startedAt);
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

}
