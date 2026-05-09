package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.domain.ObserviumMetric;
import tn.iteam.dto.ObserviumMetricDTO;

@Component
public class ObserviumMetricMapper {

    public ObserviumMetric toEntity(ObserviumMetricDTO dto) {
        return ObserviumMetric.builder()
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
