package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.iteam.domain.ApiResponse;
import tn.iteam.dto.SourceAvailabilityDTO;
import tn.iteam.integration.*;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringResponse;
import tn.iteam.monitoring.service.MonitoringAggregationService;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.websocket.MonitoringWebSocketPublisher;
import tn.iteam.websocket.ZkBioWebSocketPublisher;

import java.util.List;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final CameraIntegrationService cameraIntegrationService;
    private final MonitoringAggregationService aggregationService;
    private final SourceAvailabilityService sourceAvailabilityService;
    private final ZabbixIntegrationService zabbixIntegrationService;
    private final ObserviumIntegrationService observiumIntegrationService;
    private final IntegrationService zkBioIntegrationService;
    private final MonitoringWebSocketPublisher monitoringWebSocketPublisher;
    private final ZkBioWebSocketPublisher zkBioWebSocketPublisher;

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
        zabbixIntegrationService.refresh();
        observiumIntegrationService.refresh();
        zkBioIntegrationService.refresh();
        zkBioIntegrationService.refreshAttendance();
        cameraIntegrationService.refresh();
        publishUnifiedSnapshots();
        publishZkBioSnapshots();

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
        zabbixIntegrationService.refresh();
        monitoringWebSocketPublisher.publishProblemsFromSnapshot(MonitoringSourceType.ZABBIX);
        monitoringWebSocketPublisher.publishMetricsFromSnapshot(MonitoringSourceType.ZABBIX);

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
        observiumIntegrationService.refresh();
        monitoringWebSocketPublisher.publishProblemsFromSnapshot(MonitoringSourceType.OBSERVIUM);
        monitoringWebSocketPublisher.publishMetricsFromSnapshot(MonitoringSourceType.OBSERVIUM);

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
        cameraIntegrationService.refresh();

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("CAMERA COLLECTED")
                        .source("SYSTEM")
                        .build()
        );
    }

    private void publishUnifiedSnapshots() {
        monitoringWebSocketPublisher.publishProblemsFromSnapshot(MonitoringSourceType.ZABBIX);
        monitoringWebSocketPublisher.publishMetricsFromSnapshot(MonitoringSourceType.ZABBIX);
        monitoringWebSocketPublisher.publishProblemsFromSnapshot(MonitoringSourceType.OBSERVIUM);
        monitoringWebSocketPublisher.publishMetricsFromSnapshot(MonitoringSourceType.OBSERVIUM);
        monitoringWebSocketPublisher.publishProblemsFromSnapshot(MonitoringSourceType.ZKBIO);
        monitoringWebSocketPublisher.publishMetricsFromSnapshot(MonitoringSourceType.ZKBIO);
    }

    private void publishZkBioSnapshots() {
        zkBioWebSocketPublisher.publishAttendanceFromSnapshot();
        zkBioWebSocketPublisher.publishDevicesFromSnapshot();
        zkBioWebSocketPublisher.publishStatusFromSnapshot();
    }

}
