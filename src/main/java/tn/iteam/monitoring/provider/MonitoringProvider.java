package tn.iteam.monitoring.provider;

import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;

import java.util.List;

public interface MonitoringProvider {

    MonitoringSourceType getSourceType();

    List<UnifiedMonitoringHostDTO> getHosts();

    List<UnifiedMonitoringProblemDTO> getProblems();

    List<UnifiedMonitoringMetricDTO> getMetrics();
}
