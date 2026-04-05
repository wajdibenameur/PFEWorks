package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.service.ZabbixMetricsService;

@RestController
@RequestMapping("/api/zabbix/metrics")
@RequiredArgsConstructor
public class ZabbixMetricsController {

    private final ZabbixMetricsService service;

    @PostMapping("/collect")
    public ResponseEntity<String> collect() {
        service.collectMetrics();
        return ResponseEntity.ok("ZABBIX METRICS COLLECTED");
    }
}