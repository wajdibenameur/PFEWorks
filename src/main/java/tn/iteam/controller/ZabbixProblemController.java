package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.service.MonitoringAggregationService;

import java.util.List;

@RestController
@RequestMapping("/api/zabbix")
@RequiredArgsConstructor
/**
 * Temporary compatibility controller for legacy frontend calls.
 *
 * Keep this endpoint while the frontend still consumes {@code /api/zabbix/active}.
 * The response is already derived from the unified monitoring aggregation flow.
 *
 * When the frontend fully migrates to {@code /api/monitoring/*}, this controller
 * can be moved to {@code depl}.
 */
public class ZabbixProblemController {

    private final MonitoringAggregationService aggregationService;

    @GetMapping("/active")
    public List<ZabbixProblemDTO> allActive() {
        return aggregationService.getProblems(MonitoringSourceType.ZABBIX).getData().stream()
                .map(problem -> ZabbixProblemDTO.builder()
                        .problemId(problem.getProblemId())
                        .host(problem.getHostName())
                        .port(problem.getPort())
                        .hostId(problem.getHostId())
                        .description(problem.getDescription())
                        .severity(problem.getSeverity())
                        .active(problem.isActive())
                        .source(problem.getSource().name())
                        .eventId(problem.getEventId())
                        .ip(problem.getIp())
                        .startedAt(problem.getStartedAt())
                        .startedAtFormatted(problem.getStartedAtFormatted())
                        .resolvedAt(problem.getResolvedAt())
                        .resolvedAtFormatted(problem.getResolvedAtFormatted())
                        .status(problem.getStatus())
                        .build())
                .toList();
    }
}
