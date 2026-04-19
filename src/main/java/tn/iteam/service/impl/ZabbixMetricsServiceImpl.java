package tn.iteam.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
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
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.ZabbixDataQualityService;
import tn.iteam.service.ZabbixMetricsService;
import tn.iteam.service.support.IntegrationExecutionHelper;
import tn.iteam.util.MonitoringConstants;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ZabbixMetricsServiceImpl implements ZabbixMetricsService {

    private static final Logger log = LoggerFactory.getLogger(ZabbixMetricsServiceImpl.class);
    private static final String FETCHING_METRICS_MESSAGE = "Fetching Zabbix metrics";
    private static final String NO_METRICS_MESSAGE = "No Zabbix metrics to persist";
    private static final String EMPTY_METRIC_LOG_TEMPLATE = "Skipping metric with empty hostId/itemId: {}";
    private static final String SYNCHRONIZATION_FAILED_TEMPLATE = "Zabbix metrics synchronization failed: {}";
    private static final String UNEXPECTED_ERROR_MESSAGE = "Unexpected error fetching Zabbix metrics";

    private final ZabbixAdapter adapter;
    private final ZabbixMetricMapper mapper;
    private final ZabbixMetricRepository repository;
    private final SourceAvailabilityService availabilityService;
    private final ZabbixDataQualityService dataQualityService;
    private final IntegrationExecutionHelper executionHelper;

    @Override
    public List<ZabbixMetric> getPersistedMetricsSnapshot() {
        return repository.findAll();
    }

    @Override
    public List<ZabbixMetric> synchronizeAndGetPersistedMetricsSnapshot() {
        List<ZabbixMetric> synced = fetchAndSaveMetrics();
        List<ZabbixMetric> persisted = getPersistedMetricsSnapshot();

        if (synced.isEmpty() && !persisted.isEmpty() && !availabilityService.isAvailable(MonitoringConstants.SOURCE_ZABBIX)) {
            availabilityService.markDegraded(
                    MonitoringConstants.SOURCE_ZABBIX,
                    "Live Zabbix metrics unavailable, using last persisted snapshot"
            );
            log.warn("Zabbix unavailable, using last persisted metrics snapshot ({} metrics)", persisted.size());
        }

        return persisted;
    }

    @Override
    @Transactional
    public List<ZabbixMetric> fetchAndSaveMetrics() {
        return fetchAndSaveMetrics(null);
    }

    @Override
    @Transactional
    public List<ZabbixMetric> fetchAndSaveMetrics(JsonNode hosts) {
        log.info(FETCHING_METRICS_MESSAGE);
        return executionHelper.execute(
                availabilityService,
                log,
                MonitoringConstants.SOURCE_ZABBIX,
                MonitoringConstants.SOURCE_LABEL_ZABBIX,
                SYNCHRONIZATION_FAILED_TEMPLATE,
                UNEXPECTED_ERROR_MESSAGE,
                List.of(),
                () -> {
                    List<ZabbixMetricDTO> dtos = hosts == null
                            ? adapter.fetchMetricsAndMap()
                            : adapter.fetchMetricsAndMap(hosts);
                    log.info("Mapped {} Zabbix metrics DTOs", dtos.size());

                    if (dtos.isEmpty()) {
                        log.warn(NO_METRICS_MESSAGE);
                        return List.of();
                    }

                    List<ZabbixMetric> entitiesToSave = new ArrayList<>();

                    for (ZabbixMetricDTO dto : dtos) {
                        if (dto.getHostId() == null || dto.getHostId().isBlank()
                                || dto.getItemId() == null || dto.getItemId().isBlank()
                                || dto.getTimestamp() == null) {
                            log.warn(EMPTY_METRIC_LOG_TEMPLATE, dto);
                            continue;
                        }

                        ZabbixMetric entity = mapper.toEntity(dto);

                        ZabbixMetric finalEntity = repository.findByHostIdAndItemIdAndTimestamp(
                                        dto.getHostId(),
                                        dto.getItemId(),
                                        dto.getTimestamp()
                                )
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

                    dataQualityService.logMetricQualitySummary(saved);
                    log.info("Saved/Updated {} Zabbix metrics", saved.size());
                    log.info("DB count after save: {}", repository.count());

                    return saved;
                }
        );
    }
}
