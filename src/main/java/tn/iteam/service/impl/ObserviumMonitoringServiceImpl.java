package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.observium.ObserviumAdapter;
import tn.iteam.mapper.ObserviumMonitoringMapper;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.service.ObserviumMonitoringService;

import java.util.List;

/**
 * Implementation of ObserviumMonitoringService.
 * This service is dedicated to the unified monitoring layer and provides
 * hosts and problems data for the monitoring aggregation.
 * It delegates to ObserviumAdapter which handles the Redis fallback logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObserviumMonitoringServiceImpl implements ObserviumMonitoringService {

    private final ObserviumAdapter observiumAdapter;
    private final ObserviumMonitoringMapper monitoringMapper;

    @Override
    public List<UnifiedMonitoringHostDTO> fetchMonitoringHosts() {
        log.debug("Fetching monitoring hosts from Observium");
        return monitoringMapper.toHosts(observiumAdapter.fetchAll());
    }

    @Override
    public List<UnifiedMonitoringProblemDTO> fetchMonitoringProblems() {
        log.debug("Fetching monitoring problems from Observium");
        return monitoringMapper.toProblems(observiumAdapter.fetchProblems());
    }

    @Override
    public List<UnifiedMonitoringMetricDTO> fetchMonitoringMetrics() {
        log.debug("Fetching monitoring metrics from Observium");
        return observiumAdapter.fetchMetrics().stream()
                .map(monitoringMapper::toMetric)
                .toList();
    }
}
