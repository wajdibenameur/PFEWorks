package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.domain.ZkBioMetric;
import tn.iteam.dto.ZkBioMetricDTO;

@Component
public class ZkBioMetricMapper {

    public ZkBioMetric toEntity(ZkBioMetricDTO dto) {
        return ZkBioMetric.builder()
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
