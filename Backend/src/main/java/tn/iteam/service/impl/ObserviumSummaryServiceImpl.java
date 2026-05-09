package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.service.MonitoringAggregationService;
import tn.iteam.service.ObserviumSummaryService;
import tn.iteam.util.MonitoringConstants;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ObserviumSummaryServiceImpl implements ObserviumSummaryService {

    private final MonitoringAggregationService monitoringAggregationService;

    @Override
    public Map<String, Long> getSummary() {
        var hosts = monitoringAggregationService.getHosts(MonitoringSourceType.OBSERVIUM).getData();
        var problems = monitoringAggregationService.getProblems(MonitoringSourceType.OBSERVIUM).getData();

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
