package tn.iteam.monitoring.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.service.ZkBioMonitoringService;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ZkBioMonitoringProvider implements MonitoringProvider {

    private final ZkBioMonitoringService zkBioMonitoringService;

    @Override
    public MonitoringSourceType getSourceType() {
        return MonitoringSourceType.ZKBIO;
    }

    @Override
    public List<UnifiedMonitoringHostDTO> getHosts() {
        return zkBioMonitoringService.fetchMonitoringHosts();
    }

    @Override
    public List<UnifiedMonitoringProblemDTO> getProblems() {
        return zkBioMonitoringService.fetchMonitoringProblems();
    }

    @Override
    public List<UnifiedMonitoringMetricDTO> getMetrics() {
        return List.of();
    }
}
