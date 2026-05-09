package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ZabbixMetricDTO;

@Component
public class ZabbixMetricMapper {

    public ZabbixMetric toEntity(ZabbixMetricDTO dto) {
        return ZabbixMetric.builder()
                .hostId(dto.getHostId())
                .hostName(dto.getHostName())
                .itemId(dto.getItemId())
                .metricName(dto.getMetricName() != null ? dto.getMetricName() : dto.getMetricKey())
                .metricKey(dto.getMetricKey())
                .source(dto.getSource() != null ? dto.getSource() : "Zabbix")
                .valueType(dto.getValueType() != null ? dto.getValueType() : 0)
                .status(dto.getStatus() != null ? dto.getStatus() : "UNKNOWN")
                .units(dto.getUnits())
                .value(dto.getValue())
                .timestamp(dto.getTimestamp())
                .ip(dto.getIp())
                .port(dto.getPort())
                .build();
    }

    public ZabbixMetricDTO toDTO(ZabbixMetric entity) {
        return ZabbixMetricDTO.builder()
                .hostId(entity.getHostId())
                .hostName(entity.getHostName())
                .itemId(entity.getItemId())
                .metricName(entity.getMetricName())
                .metricKey(entity.getMetricKey())
                .source(entity.getSource())
                .valueType(entity.getValueType())
                .status(entity.getStatus())
                .units(entity.getUnits())
                .value(entity.getValue())
                .timestamp(entity.getTimestamp())
                .ip(entity.getIp())
                .port(entity.getPort())
                .build();
    }
}
