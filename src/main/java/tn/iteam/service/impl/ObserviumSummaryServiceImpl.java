package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.provider.ObserviumMonitoringProvider;
import tn.iteam.service.ObserviumSummaryService;
import tn.iteam.util.MonitoringConstants;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ObserviumSummaryServiceImpl implements ObserviumSummaryService {

    private final ObserviumMonitoringProvider observiumMonitoringProvider;

    @Override
    public Map<String, Long> getSummary() {
        List<UnifiedMonitoringHostDTO> hosts = observiumMonitoringProvider.getHosts();
        List<UnifiedMonitoringProblemDTO> problems = observiumMonitoringProvider.getProblems();

        long totalDevices = hosts.size();
        long downDevices = hosts.stream()
                .map(UnifiedMonitoringHostDTO::getStatus)
                .filter(MonitoringConstants.STATUS_DOWN::equalsIgnoreCase)
                .count();
        long activeAlerts = problems.stream()
                .filter(UnifiedMonitoringProblemDTO::isActive)
                .count();

        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("totalDevices", totalDevices);
        summary.put("downDevices", downDevices);
        summary.put("activeAlerts", activeAlerts);
        return summary;
    }
}
