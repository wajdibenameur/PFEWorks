package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.mapper.ZabbixMonitoringMapper;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.service.ZabbixMonitoringService;

import java.util.List;

/**
 * Implementation of ZabbixMonitoringService.
 * This service is dedicated to the unified monitoring layer and provides
 * hosts, problems and metrics data for the monitoring aggregation.
 * It delegates to ZabbixAdapter which handles the Redis fallback logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZabbixMonitoringServiceImpl implements ZabbixMonitoringService {

    private final ZabbixAdapter zabbixAdapter;
    private final ZabbixMonitoringMapper monitoringMapper;

    @Override
    public List<UnifiedMonitoringHostDTO> fetchMonitoringHosts() {
        log.debug("Fetching monitoring hosts from Zabbix");
        return zabbixAdapter.fetchAll().stream()
                .map(monitoringMapper::toHostFromServiceStatus)
                .toList();
    }

    @Override
    public List<UnifiedMonitoringProblemDTO> fetchMonitoringProblems() {
        log.debug("Fetching monitoring problems from Zabbix");
        return zabbixAdapter.fetchProblems().stream()
                .map(monitoringMapper::toProblem)
                .toList();
    }

    @Override
    public List<UnifiedMonitoringMetricDTO> fetchMonitoringMetrics() {
        log.debug("Fetching monitoring metrics from Zabbix");
        return zabbixAdapter.fetchMetricsAndMap().stream()
                .map(monitoringMapper::toMetricFromDTO)
                .toList();
    }
}