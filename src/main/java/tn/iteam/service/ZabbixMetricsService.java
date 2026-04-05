package tn.iteam.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.repository.ZabbixMetricRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ZabbixMetricsService {

    private static final Logger log = LoggerFactory.getLogger(ZabbixMetricsService.class);

    private final ZabbixAdapter adapter;
    private final ZabbixMetricRepository repository;

    @Transactional
    public void collectMetrics() {

        log.info("===== COLLECTING ZABBIX METRICS =====");

        long start = System.currentTimeMillis();

        List<ZabbixMetric> metrics = adapter.fetchMetricsAndMap();

        if (!metrics.isEmpty()) {
            repository.saveAll(metrics);
            log.info("{} metrics saved to database", metrics.size());
        }

        long duration = System.currentTimeMillis() - start;

        log.info("===== METRICS COLLECTION DONE ({} ms) =====", duration);
    }
}