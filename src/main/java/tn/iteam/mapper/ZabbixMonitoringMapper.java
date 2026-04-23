package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.domain.MonitoredHost;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZabbixMetricDTO;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;

/**
 * Mapper for converting Zabbix domain objects to unified monitoring DTOs.
 */
@Component
public class ZabbixMonitoringMapper {

    /**
     * Converts from MonitoredHost entity (JPA persisted).
     */
    public UnifiedMonitoringHostDTO toHost(MonitoredHost host) {
        return UnifiedMonitoringHostDTO.builder()
                .id(MonitoringSourceType.ZABBIX + ":" + host.getHostId())
                .source(MonitoringSourceType.ZABBIX)
                .hostId(host.getHostId())
                .name(host.getName())
                .ip(host.getIp())
                .port(host.getPort())
                .status("UNKNOWN")
                .category("SERVER")
                .build();
    }

    /**
     * Converts from ServiceStatusDTO (adapter output).
     */
    public UnifiedMonitoringHostDTO toHostFromServiceStatus(ServiceStatusDTO dto) {
        String hostId = normalizeText(dto.getIp());
        return UnifiedMonitoringHostDTO.builder()
                .id(MonitoringSourceType.ZABBIX + ":" + hostId)
                .source(MonitoringSourceType.ZABBIX)
                .hostId(hostId)
                .name(normalizeText(dto.getName()))
                .ip(normalizeText(dto.getIp()))
                .port(dto.getPort())
                .protocol(normalizeText(dto.getProtocol()))
                .status(normalizeText(dto.getStatus()))
                .category(normalizeText(dto.getCategory()))
                .build();
    }

    public UnifiedMonitoringProblemDTO toProblem(ZabbixProblemDTO dto) {
        return UnifiedMonitoringProblemDTO.builder()
                .id(MonitoringSourceType.ZABBIX + ":" + dto.getProblemId())
                .source(MonitoringSourceType.ZABBIX)
                .problemId(dto.getProblemId())
                .eventId(dto.getEventId())
                .hostId(dto.getHostId())
                .hostName(dto.getHost())
                .description(dto.getDescription())
                .severity(dto.getSeverity())
                .active(Boolean.TRUE.equals(dto.getActive()))
                .status(dto.getStatus())
                .ip(dto.getIp())
                .port(dto.getPort())
                .startedAt(dto.getStartedAt())
                .startedAtFormatted(dto.getStartedAtFormatted())
                .resolvedAt(dto.getResolvedAt())
                .resolvedAtFormatted(dto.getResolvedAtFormatted())
                .build();
    }

    /**
     * Converts from ZabbixMetric entity (JPA persisted).
     */
    public UnifiedMonitoringMetricDTO toMetric(ZabbixMetric entity) {
        return UnifiedMonitoringMetricDTO.builder()
                .id(MonitoringSourceType.ZABBIX + ":" + entity.getHostId() + ":" + entity.getItemId() + ":" + entity.getTimestamp())
                .source(MonitoringSourceType.ZABBIX)
                .hostId(entity.getHostId())
                .hostName(entity.getHostName())
                .itemId(entity.getItemId())
                .metricName(entity.getMetricName())
                .metricKey(entity.getMetricKey())
                .valueType(entity.getValueType())
                .status(entity.getStatus())
                .units(entity.getUnits())
                .value(entity.getValue())
                .timestamp(entity.getTimestamp())
                .ip(entity.getIp())
                .port(entity.getPort())
                .build();
    }

    /**
     * Converts from ZabbixMetricDTO (adapter output).
     */
    public UnifiedMonitoringMetricDTO toMetricFromDTO(ZabbixMetricDTO dto) {
        return UnifiedMonitoringMetricDTO.builder()
                .id(MonitoringSourceType.ZABBIX + ":" + dto.getHostId() + ":" + dto.getItemId() + ":" + dto.getTimestamp())
                .source(MonitoringSourceType.ZABBIX)
                .hostId(dto.getHostId())
                .hostName(dto.getHostName())
                .itemId(dto.getItemId())
                .metricName(dto.getMetricName())
                .metricKey(dto.getMetricKey())
                .valueType(dto.getValueType())
                .status(dto.getStatus())
                .units(dto.getUnits())
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
}
