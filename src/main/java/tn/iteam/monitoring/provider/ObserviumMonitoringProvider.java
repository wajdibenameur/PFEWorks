package tn.iteam.monitoring.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.service.ObserviumMonitoringService;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ObserviumMonitoringProvider implements MonitoringProvider {

    private final ObserviumMonitoringService observiumMonitoringService;

    @Override
    public MonitoringSourceType getSourceType() {
        return MonitoringSourceType.OBSERVIUM;
    }

    @Override
    public List<UnifiedMonitoringHostDTO> getHosts() {
        return observiumMonitoringService.fetchMonitoringHosts();
    }

    @Override
    public List<UnifiedMonitoringProblemDTO> getProblems() {
        return observiumMonitoringService.fetchMonitoringProblems();
    }

    @Override
    public List<UnifiedMonitoringMetricDTO> getMetrics() {
        return List.of();
    }
}
