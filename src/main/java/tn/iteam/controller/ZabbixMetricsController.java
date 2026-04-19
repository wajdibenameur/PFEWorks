package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.dto.ZabbixMetricDTO;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.service.MonitoringAggregationService;

import java.util.List;

@RestController
@RequestMapping("/api/zabbix/metrics")
@RequiredArgsConstructor
public class ZabbixMetricsController {

    private final MonitoringAggregationService aggregationService;

    @GetMapping
    public List<ZabbixMetricDTO> getMetrics() {
        return aggregationService.getMetrics(MonitoringSourceType.ZABBIX).getData().stream()
                .map(metric -> ZabbixMetricDTO.builder()
                        .hostId(metric.getHostId())
                        .hostName(metric.getHostName())
                        .itemId(metric.getItemId())
                        .metricKey(metric.getMetricKey())
                        .value(metric.getValue())
                        .timestamp(metric.getTimestamp())
                        .ip(metric.getIp())
                        .port(metric.getPort())
                        .build())
                .toList();
    }
}
