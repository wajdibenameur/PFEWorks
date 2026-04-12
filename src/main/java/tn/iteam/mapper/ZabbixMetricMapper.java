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
                .metricKey(dto.getMetricKey())
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
                .metricKey(entity.getMetricKey())
                .value(entity.getValue())
                .timestamp(entity.getTimestamp())
                .ip(entity.getIp())
                .port(entity.getPort())
                .build();
    }
}