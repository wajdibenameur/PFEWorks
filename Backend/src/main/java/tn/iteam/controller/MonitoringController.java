package tn.iteam.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import tn.iteam.domain.ApiResponse;
import tn.iteam.dto.InterfaceDTO;
import tn.iteam.dto.SourceAvailabilityDTO;
import tn.iteam.integration.IntegrationServiceRegistry;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringResponse;
import tn.iteam.monitoring.service.MonitoringAggregationService;
import tn.iteam.service.SnmpInterfaceService;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.support.MonitoringFreshnessService;
import tn.iteam.service.support.MonitoringSnapshotPublicationService;

import java.util.List;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
@Tag(name = "Monitoring", description = "API unifiee de supervision et de declenchement de collecte")
public class MonitoringController {

    private final MonitoringAggregationService aggregationService;
    private final SourceAvailabilityService sourceAvailabilityService;
    private final IntegrationServiceRegistry integrationServiceRegistry;
    private final MonitoringSnapshotPublicationService snapshotPublicationService;
    private final SnmpInterfaceService snmpInterfaceService;
    private final MonitoringFreshnessService monitoringFreshnessService;

    @GetMapping("/problems")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_ALERTS)")
    @Operation(summary = "Lister les incidents", description = "Retourne les incidents agreges de toutes les sources de supervision.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incidents recuperes avec succes")
    })
    public UnifiedMonitoringResponse<List<UnifiedMonitoringProblemDTO>> getProblems() {
        return aggregationService.getProblems((String) null);
    }

    @GetMapping("/metrics")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_METRICS)")
    @Operation(summary = "Lister les metriques", description = "Retourne les metriques agregees de toutes les sources de supervision.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Metriques recuperees avec succes")
    })
    public UnifiedMonitoringResponse<List<UnifiedMonitoringMetricDTO>> getMetrics() {
        return aggregationService.getMetrics((String) null);
    }

    @GetMapping("/hosts")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_HOSTS)")
    @Operation(summary = "Lister les hotes supervises", description = "Retourne les hotes agreges de toutes les sources de supervision.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hotes recuperes avec succes")
    })
    public UnifiedMonitoringResponse<List<UnifiedMonitoringHostDTO>> getHosts() {
        return aggregationService.getHosts((String) null);
    }

    @GetMapping("/sources/health")
    @PreAuthorize("hasAnyAuthority('VIEW_DASHBOARD','VIEW_ZABBIX','VIEW_SNMP','VIEW_CAMERA','VIEW_ACCESS_POINT')")
    @Operation(summary = "Consulter l'etat des sources", description = "Retourne l'etat de disponibilite de chaque source de supervision.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Etat des sources recupere avec succes")
    })
    public List<SourceAvailabilityDTO> getSourceHealth() {
        return sourceAvailabilityService.getAll();
    }

    @GetMapping("/interfaces")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_METRICS)")
    @Operation(summary = "Lister les interfaces reseau SNMP", description = "Retourne les interfaces SNMP avec etat, compteurs et bande passante calculee.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Interfaces recuperees avec succes")
    })
    public List<InterfaceDTO> getSnmpInterfaces() {
        return snmpInterfaceService.getAllInterfaces();
    }

    @PostMapping("/collect")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).REFRESH_DASHBOARD)")
    @Operation(summary = "Declencher une collecte complete", description = "Lance une collecte asynchrone pour Zabbix, SNMP et Cameras.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Collecte globale declenchee avec succes")
    })
    public Mono<ResponseEntity<ApiResponse<Void>>> collectAll() {
        monitoringFreshnessService.invalidateSource(MonitoringSourceType.ZABBIX.name());
        monitoringFreshnessService.invalidateSource(MonitoringSourceType.SNMP.name());
        monitoringFreshnessService.invalidateSource(MonitoringSourceType.CAMERA.name());
        return Mono.whenDelayError(
                        integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX).refreshAsync(),
                        integrationServiceRegistry.getRequired(MonitoringSourceType.SNMP).refreshAsync(),
                        integrationServiceRegistry.getRequired(MonitoringSourceType.CAMERA).refreshAsync()
                )
                .then(Mono.fromRunnable(() -> snapshotPublicationService.publishMonitoringSnapshots(List.of(
                        MonitoringSourceType.ZABBIX,
                        MonitoringSourceType.SNMP
                ))))
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
    @Operation(summary = "Declencher une collecte Zabbix", description = "Lance une collecte asynchrone complete pour la source Zabbix.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Collecte Zabbix declenchee avec succes")
    })
    public Mono<ResponseEntity<ApiResponse<Void>>> collectZabbix() {
        monitoringFreshnessService.invalidateSource(MonitoringSourceType.ZABBIX.name());
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

    @PostMapping("/collect/snmp")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).REFRESH_DASHBOARD)")
    @Operation(summary = "Declencher une collecte SNMP", description = "Lance une collecte asynchrone complete pour la source SNMP.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Collecte SNMP declenchee avec succes")
    })
    public Mono<ResponseEntity<ApiResponse<Void>>> collectSnmp() {
        monitoringFreshnessService.invalidateSource(MonitoringSourceType.SNMP.name());
        return integrationServiceRegistry.getRequired(MonitoringSourceType.SNMP).refreshAsync()
                .then(Mono.fromRunnable(() -> snapshotPublicationService.publishMonitoringSnapshots(MonitoringSourceType.SNMP)))
                .thenReturn(ResponseEntity.ok(
                        ApiResponse.<Void>builder()
                                .success(true)
                                .message("SNMP COLLECTED")
                                .source("SYSTEM")
                                .build()
                ));
    }

    @PostMapping("/collect/camera")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).REFRESH_DASHBOARD)")
    @Operation(summary = "Declencher une collecte Cameras", description = "Lance une collecte asynchrone complete pour l'inventaire cameras.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Collecte cameras declenchee avec succes")
    })
    public Mono<ResponseEntity<ApiResponse<Void>>> collectCamera() {
        monitoringFreshnessService.invalidateSource(MonitoringSourceType.CAMERA.name());
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
