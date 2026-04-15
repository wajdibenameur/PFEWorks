package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ZabbixMetricDTO;
import tn.iteam.mapper.ZabbixMetricMapper;
import tn.iteam.service.ZabbixMetricsService;

import java.util.List;

@RestController
@RequestMapping("/api/zabbix/metrics")
@RequiredArgsConstructor
public class ZabbixMetricsController {

    private final ZabbixMetricsService service;
    private final ZabbixMetricMapper metricMapper;

    @GetMapping
    public List<ZabbixMetricDTO> getMetrics() {
        return service.getPersistedMetricsSnapshot().stream()
                .map(metricMapper::toDTO)
                .toList();
    }
}
