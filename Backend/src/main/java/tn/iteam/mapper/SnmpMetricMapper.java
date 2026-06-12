package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.domain.SnmpMetric;
import tn.iteam.dto.SnmpMetricDTO;

@Component
public class SnmpMetricMapper {

    public SnmpMetric toEntity(SnmpMetricDTO dto) {
        return SnmpMetric.builder()
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
