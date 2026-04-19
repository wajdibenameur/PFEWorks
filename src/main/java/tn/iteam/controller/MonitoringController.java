package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.iteam.domain.ApiResponse;
import tn.iteam.dto.SourceAvailabilityDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringResponse;
import tn.iteam.monitoring.service.MonitoringAggregationService;
import tn.iteam.service.MonitoringService;
import tn.iteam.service.SourceAvailabilityService;

import java.util.List;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService monitoringService;
    private final MonitoringAggregationService aggregationService;
    private final SourceAvailabilityService sourceAvailabilityService;

    @GetMapping("/problems")
    public UnifiedMonitoringResponse<List<UnifiedMonitoringProblemDTO>> getProblems() {
        return aggregationService.getProblems((String) null);
    }

    @GetMapping("/metrics")
    public UnifiedMonitoringResponse<List<UnifiedMonitoringMetricDTO>> getMetrics() {
        return aggregationService.getMetrics((String) null);
    }

    @GetMapping("/hosts")
    public UnifiedMonitoringResponse<List<UnifiedMonitoringHostDTO>> getHosts() {
        return aggregationService.getHosts((String) null);
    }

    @GetMapping("/sources/health")
    public List<SourceAvailabilityDTO> getSourceHealth() {
        return sourceAvailabilityService.getAll();
    }

    @PostMapping("/collect")
    public ResponseEntity<ApiResponse<Void>> collectAll() {

        monitoringService.collectAll();

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("ALL SERVICES COLLECTED")
                        .source("SYSTEM")
                        .build()
        );
    }

    @PostMapping("/collect/zabbix")
    public ResponseEntity<ApiResponse<Void>> collectZabbix() {

        monitoringService.collectZabbix();

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("ZABBIX COLLECTED")
                        .source("SYSTEM")
                        .build()
        );
    }

    @PostMapping("/collect/observium")
    public ResponseEntity<ApiResponse<Void>> collectObservium() {

        monitoringService.collectObservium();

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("OBSERVIUM COLLECTED")
                        .source("SYSTEM")
                        .build()
        );
    }

    @PostMapping("/collect/camera")
    public ResponseEntity<ApiResponse<Void>> collectCamera() {

        monitoringService.collectCamera();

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("CAMERA COLLECTED")
                        .source("SYSTEM")
                        .build()
        );
    }

}
