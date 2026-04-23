package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import tn.iteam.domain.ApiResponse;
import tn.iteam.dto.SourceAvailabilityDTO;
import tn.iteam.integration.IntegrationServiceRegistry;
import tn.iteam.integration.ZkBioIntegrationOperations;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringResponse;
import tn.iteam.monitoring.service.MonitoringAggregationService;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.support.MonitoringSnapshotPublicationService;

import java.util.List;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringAggregationService aggregationService;
    private final SourceAvailabilityService sourceAvailabilityService;
    private final IntegrationServiceRegistry integrationServiceRegistry;
    private final ZkBioIntegrationOperations zkBioIntegrationOperations;
    private final MonitoringSnapshotPublicationService snapshotPublicationService;

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
        integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX).refresh();
        integrationServiceRegistry.getRequired(MonitoringSourceType.OBSERVIUM).refresh();
        integrationServiceRegistry.getRequired(MonitoringSourceType.ZKBIO).refresh();
        zkBioIntegrationOperations.refreshAttendance();
        integrationServiceRegistry.getRequired(MonitoringSourceType.CAMERA).refresh();
        snapshotPublicationService.publishMonitoringSnapshots(List.of(
                MonitoringSourceType.ZABBIX,
                MonitoringSourceType.OBSERVIUM,
                MonitoringSourceType.ZKBIO
        ));
        snapshotPublicationService.publishZkBioSnapshots();

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .success(true)
                        .message("ALL SERVICES COLLECTED")
                        .source("SYSTEM")
                        .build()
        );
    }

    @PostMapping("/collect/zabbix")
    public Mono<ResponseEntity<ApiResponse<Void>>> collectZabbix() {
        return integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX).refreshAsync()
                .then(Mono.fromRunnable(() -> snapshotPublicationService.publishMonitoringSnapshots(MonitoringSourceType.ZABBIX)))
                .thenReturn(ResponseEntity.ok(
                        ApiResponse.<Void>builder()
                                .success(true)
                                .message("ZABBIX COLLECTED")
                                .source("SYSTEM")
                                .build()
                ));
    }

    @PostMapping("/collect/observium")
    public Mono<ResponseEntity<ApiResponse<Void>>> collectObservium() {
        return integrationServiceRegistry.getRequired(MonitoringSourceType.OBSERVIUM).refreshAsync()
                .then(Mono.fromRunnable(() -> snapshotPublicationService.publishMonitoringSnapshots(MonitoringSourceType.OBSERVIUM)))
                .thenReturn(ResponseEntity.ok(
                        ApiResponse.<Void>builder()
                                .success(true)
                                .message("OBSERVIUM COLLECTED")
                                .source("SYSTEM")
                                .build()
                ));
    }

    @PostMapping("/collect/camera")
    public Mono<ResponseEntity<ApiResponse<Void>>> collectCamera() {
        return integrationServiceRegistry.getRequired(MonitoringSourceType.CAMERA).refreshAsync()
                .thenReturn(ResponseEntity.ok(
                        ApiResponse.<Void>builder()
                                .success(true)
                                .message("CAMERA COLLECTED")
                                .source("SYSTEM")
                                .build()
                ));
    }

}
