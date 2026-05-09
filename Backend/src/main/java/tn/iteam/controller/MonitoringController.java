package tn.iteam.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import tn.iteam.domain.ApiResponse;
import tn.iteam.dto.SourceAvailabilityDTO;
import tn.iteam.integration.IntegrationServiceRegistry;
import tn.iteam.integration.ZkBioRefreshOrchestrationService;
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
@Tag(name = "Monitoring", description = "API unifiée de supervision et de déclenchement de collecte")
public class MonitoringController {

    private final MonitoringAggregationService aggregationService;
    private final SourceAvailabilityService sourceAvailabilityService;
    private final IntegrationServiceRegistry integrationServiceRegistry;
    private final ZkBioRefreshOrchestrationService zkBioRefreshOrchestrationService;
    private final MonitoringSnapshotPublicationService snapshotPublicationService;

    @GetMapping("/problems")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_ALERTS)")
    @Operation(summary = "Lister les incidents", description = "Retourne les incidents agrégés de toutes les sources de supervision.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incidents récupérés avec succès")
    })
    public UnifiedMonitoringResponse<List<UnifiedMonitoringProblemDTO>> getProblems() {
        return aggregationService.getProblems((String) null);
    }

    @GetMapping("/metrics")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_METRICS)")
    @Operation(summary = "Lister les métriques", description = "Retourne les métriques agrégées de toutes les sources de supervision.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Métriques récupérées avec succès")
    })
    public UnifiedMonitoringResponse<List<UnifiedMonitoringMetricDTO>> getMetrics() {
        return aggregationService.getMetrics((String) null);
    }

    @GetMapping("/hosts")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_HOSTS)")
    @Operation(summary = "Lister les hôtes supervisés", description = "Retourne les hôtes agrégés de toutes les sources de supervision.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hôtes récupérés avec succès")
    })
    public UnifiedMonitoringResponse<List<UnifiedMonitoringHostDTO>> getHosts() {
        return aggregationService.getHosts((String) null);
    }

    @GetMapping("/sources/health")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_DASHBOARD)")
    @Operation(summary = "Consulter l'état des sources", description = "Retourne l'état de disponibilité de chaque source de supervision.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "État des sources récupéré avec succès")
    })
    public List<SourceAvailabilityDTO> getSourceHealth() {
        return sourceAvailabilityService.getAll();
    }

    @PostMapping("/collect")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).REFRESH_DASHBOARD)")
    @Operation(summary = "Déclencher une collecte complète", description = "Lance une collecte asynchrone pour Zabbix, Observium, ZKBio et Caméras.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Collecte globale déclenchée avec succès")
    })
    public Mono<ResponseEntity<ApiResponse<Void>>> collectAll() {
        return Mono.whenDelayError(
                        integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX).refreshAsync(),
                        integrationServiceRegistry.getRequired(MonitoringSourceType.OBSERVIUM).refreshAsync(),
                        zkBioRefreshOrchestrationService.refreshMonitoringAndAttendanceAsync(),
                        integrationServiceRegistry.getRequired(MonitoringSourceType.CAMERA).refreshAsync()
                )
                .then(Mono.fromRunnable(() -> {
                    snapshotPublicationService.publishMonitoringSnapshots(List.of(
                            MonitoringSourceType.ZABBIX,
                            MonitoringSourceType.OBSERVIUM,
                            MonitoringSourceType.ZKBIO
                    ));
                    snapshotPublicationService.publishZkBioSnapshots();
                }))
                .thenReturn(ResponseEntity.ok(
                        ApiResponse.<Void>builder()
                                .success(true)
                                .message("ALL SERVICES COLLECTED")
                                .source("SYSTEM")
                                .build()
                ));
    }

    @PostMapping("/collect/zabbix")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).REFRESH_DASHBOARD)")
    @Operation(summary = "Déclencher une collecte Zabbix", description = "Lance une collecte asynchrone complète pour la source Zabbix.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Collecte Zabbix déclenchée avec succès")
    })
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
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).REFRESH_DASHBOARD)")
    @Operation(summary = "Déclencher une collecte Observium", description = "Lance une collecte asynchrone complète pour la source Observium.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Collecte Observium déclenchée avec succès")
    })
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
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).REFRESH_DASHBOARD)")
    @Operation(summary = "Déclencher une collecte Caméras", description = "Lance une collecte asynchrone complète pour l'inventaire caméras.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Collecte caméras déclenchée avec succès")
    })
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
