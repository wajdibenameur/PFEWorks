package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.dto.SnmpMetricDTO;
import tn.iteam.dto.SnmpProblemDTO;
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
 * Mapper for converting SNMP DTOs to unified monitoring DTOs.
 * Note: the SNMP polling source does not provide timestamp fields (startedAt/resolvedAt) for alerts.
 * We use the collection time as a fallback for startedAt.
 */
@Component
public class SnmpMonitoringMapper {

    public UnifiedMonitoringHostDTO toHost(ServiceStatusDTO dto) {
        String hostName = MonitoringNormalizeUtils.normalizeText(dto.getName(), MonitoringConstants.UNKNOWN);
        String ip = MonitoringNormalizeUtils.normalizeIp(dto.getIp());
        String hostKey = hostName != null ? hostName : (ip != null ? ip : "UNKNOWN");

        return UnifiedMonitoringHostDTO.builder()
                .id(MonitoringSourceType.SNMP + ":" + hostKey)
                .source(MonitoringSourceType.SNMP)
                .hostId(hostKey)
                .name(hostName != null ? hostName : hostKey)
                .ip(ip)
                .port(dto.getPort())
                .protocol(MonitoringNormalizeUtils.normalizeText(dto.getProtocol()))
                .status(MonitoringNormalizeUtils.normalizeText(dto.getStatus(), MonitoringConstants.UNKNOWN))
                .category(MonitoringNormalizeUtils.normalizeText(dto.getCategory(), MonitoringConstants.UNKNOWN))
                .lastCheck(dto.getLastCheck())
                .build();
    }

    public UnifiedMonitoringProblemDTO toProblem(SnmpProblemDTO dto) {
        String hostName = dto.getHost() != null && !dto.getHost().isBlank() ? dto.getHost() : "UNKNOWN";
        String hostKey = dto.getHostId() != null && !dto.getHostId().isBlank() ? dto.getHostId() : hostName;

        Long startedAt = dto.getStartedAt();
        String startedAtFormatted = dto.getStartedAtFormatted();
        Long lastObservedAt = dto.getLastObservedAt();
        String lastObservedAtFormatted = dto.getLastObservedAtFormatted();
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
        if (lastObservedAt == null) {
            lastObservedAt = startedAt;
        }
        if (lastObservedAtFormatted == null) {
            lastObservedAtFormatted = MonitoringNormalizeUtils.formatTimestamp(lastObservedAt);
        }

        return UnifiedMonitoringProblemDTO.builder()
                .id(MonitoringSourceType.SNMP + ":" + dto.getProblemId())
                .source(MonitoringSourceType.SNMP)
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
                .lastObservedAt(lastObservedAt)
                .lastObservedAtFormatted(lastObservedAtFormatted)
                .resolvedAt(resolvedAt)
                .resolvedAtFormatted(resolvedAtFormatted)
                .build();
    }

    public List<UnifiedMonitoringHostDTO> toHosts(List<ServiceStatusDTO> dtos) {
        return dtos.stream()
                .map(this::toHost)
                .toList();
    }

    public List<UnifiedMonitoringProblemDTO> toProblems(List<SnmpProblemDTO> dtos) {
        return dtos.stream()
                .map(this::toProblem)
                .toList();
    }

    public UnifiedMonitoringMetricDTO toMetric(SnmpMetricDTO dto) {
        return UnifiedMonitoringMetricDTO.builder()
                .id(MonitoringSourceType.SNMP + ":" + dto.getHostId() + ":" + dto.getItemId() + ":" + dto.getTimestamp())
                .source(MonitoringSourceType.SNMP)
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
