package tn.iteam.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.domain.ZabbixMetric;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ZabbixMetricsService {

    private static final Logger log = LoggerFactory.getLogger(ZabbixMetricsService.class);

    private final ZabbixAdapter adapter;

    public List<ZabbixMetric> fetchMetrics() {
        log.info("Fetching Zabbix metrics");
        try {
            return adapter.fetchMetricsAndMap();
        } catch (Exception e) {
            log.error("Error fetching Zabbix metrics", e);
            return List.of();
        }
    }
}