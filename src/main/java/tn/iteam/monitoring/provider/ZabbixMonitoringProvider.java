package tn.iteam.monitoring.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.service.ZabbixMonitoringService;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ZabbixMonitoringProvider implements MonitoringProvider {

    private final ZabbixMonitoringService zabbixMonitoringService;

    @Override
    public MonitoringSourceType getSourceType() {
        return MonitoringSourceType.ZABBIX;
    }

    @Override
    public List<UnifiedMonitoringHostDTO> getHosts() {
        return zabbixMonitoringService.fetchMonitoringHosts();
    }

    @Override
    public List<UnifiedMonitoringProblemDTO> getProblems() {
        return zabbixMonitoringService.fetchMonitoringProblems();
    }

    @Override
    public List<UnifiedMonitoringMetricDTO> getMetrics() {
        return zabbixMonitoringService.fetchMonitoringMetrics();
    }
}
