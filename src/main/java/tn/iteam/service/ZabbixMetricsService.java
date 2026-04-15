package tn.iteam.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ZabbixMetricDTO;
import tn.iteam.exception.IntegrationException;
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
    private final SourceAvailabilityService availabilityService;

    public List<ZabbixMetric> getPersistedMetricsSnapshot() {
        return repository.findAll();
    }

    public List<ZabbixMetric> synchronizeAndGetPersistedMetricsSnapshot() {
        List<ZabbixMetric> synced = fetchAndSaveMetrics();
        List<ZabbixMetric> persisted = getPersistedMetricsSnapshot();

        if (synced.isEmpty() && !persisted.isEmpty() && !availabilityService.isAvailable("ZABBIX")) {
            log.warn("Zabbix unavailable, using last persisted metrics snapshot ({} metrics)", persisted.size());
        }

        return persisted;
    }

    @Transactional
    public List<ZabbixMetric> fetchAndSaveMetrics() {
        return fetchAndSaveMetrics(null);
    }

    @Transactional
    public List<ZabbixMetric> fetchAndSaveMetrics(JsonNode hosts) {
        log.info("Fetching Zabbix metrics");

        try {
            List<ZabbixMetricDTO> dtos = hosts == null
                    ? adapter.fetchMetricsAndMap()
                    : adapter.fetchMetricsAndMap(hosts);
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

            availabilityService.markAvailable("ZABBIX");
            log.info("Saved/Updated {} Zabbix metrics", saved.size());
            log.info("DB count after save: {}", repository.count());

            return saved;
        } catch (IntegrationException e) {
            availabilityService.markUnavailable("ZABBIX", e.getMessage());
            log.warn("Zabbix metrics synchronization failed: {}", e.getMessage());
            return List.of();
        } catch (Exception e) {
            availabilityService.markUnavailable("ZABBIX", e.getMessage());
            log.error("Unexpected error fetching Zabbix metrics", e);
            return List.of();
        }
    }
}
