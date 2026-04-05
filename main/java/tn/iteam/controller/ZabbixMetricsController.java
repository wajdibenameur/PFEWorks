package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.service.ZabbixMetricsService;

import java.util.List;

@RestController
@RequestMapping("/api/zabbix/metrics")
@RequiredArgsConstructor
public class ZabbixMetricsController {

    private final ZabbixMetricsService service;

    @GetMapping
    public List<ZabbixMetric> getMetrics() {
        return service.fetchMetrics();
    }
}