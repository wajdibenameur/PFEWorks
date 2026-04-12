package tn.iteam.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ZabbixMetricDTO;
import tn.iteam.mapper.ZabbixMetricMapper;
import tn.iteam.repository.ZabbixMetricRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ZabbixMetricsService {

    private static final Logger log = LoggerFactory.getLogger(ZabbixMetricsService.class);

    private final ZabbixAdapter adapter;
    private final ZabbixMetricMapper mapper;
    private final ZabbixMetricRepository repository;

    public List<ZabbixMetric> getAllMetrics() {
        return repository.findAll();
    }
    public List<ZabbixMetric> fetchAndSaveMetrics() {
        log.info("Fetching Zabbix metrics");

        try {
            List<ZabbixMetricDTO> dtos = adapter.fetchMetricsAndMap();

            List<ZabbixMetric> entities = dtos.stream()
                    .map(mapper::toEntity)
                    .collect(Collectors.toList());

            return repository.saveAll(entities);

        } catch (Exception e) {
            log.error("Error fetching Zabbix metrics", e);
            return List.of();
        }
    }
}