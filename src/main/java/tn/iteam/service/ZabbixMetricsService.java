package tn.iteam.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ZabbixMetricDTO;
import tn.iteam.mapper.ZabbixMetricMapper;
import tn.iteam.repository.ZabbixMetricRepository;

import java.util.ArrayList;
import java.util.List;

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

    @Transactional
    public List<ZabbixMetric> fetchAndSaveMetrics() {
        log.info("Fetching Zabbix metrics");

        try {
            List<ZabbixMetricDTO> dtos = adapter.fetchMetricsAndMap();
            log.info("Mapped {} Zabbix metrics DTOs", dtos.size());

            if (dtos.isEmpty()) {
                log.warn("No Zabbix metrics to persist");
                return List.of();
            }

            List<ZabbixMetric> entitiesToSave = new ArrayList<>();

            for (ZabbixMetricDTO dto : dtos) {
                if (dto.getHostId() == null || dto.getHostId().isBlank()
                        || dto.getItemId() == null || dto.getItemId().isBlank()) {
                    log.warn("Skipping metric with empty hostId/itemId: {}", dto);
                    continue;
                }

                ZabbixMetric entity = mapper.toEntity(dto);

                ZabbixMetric finalEntity = repository.findByHostIdAndItemId(dto.getHostId(), dto.getItemId())
                        .map(existing -> {
                            existing.setHostName(entity.getHostName());
                            existing.setMetricKey(entity.getMetricKey());
                            existing.setValue(entity.getValue());
                            existing.setTimestamp(entity.getTimestamp());
                            existing.setIp(entity.getIp());
                            existing.setPort(entity.getPort());
                            return existing;
                        })
                        .orElse(entity);

                entitiesToSave.add(finalEntity);
            }

            List<ZabbixMetric> saved = repository.saveAll(entitiesToSave);
            repository.flush();

            log.info("Saved/Updated {} Zabbix metrics", saved.size());
            log.info("DB count after save: {}", repository.count());

            return saved;

        } catch (Exception e) {
            log.error("Error fetching Zabbix metrics", e);
            return List.of();
        }
    }
}