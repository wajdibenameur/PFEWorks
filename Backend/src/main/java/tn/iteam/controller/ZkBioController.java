package tn.iteam.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import tn.iteam.domain.ApiResponse;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZkBioAttendanceDTO;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.integration.ZkBioIntegrationOperations;
import tn.iteam.service.ZkBioServiceInterface;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/zkbio")
@RequiredArgsConstructor
@Tag(name = "ZKBio", description = "API de supervision et de collecte pour la source ZKBio")
public class ZkBioController {

    private final ZkBioServiceInterface zkBioService;
    private final ZkBioIntegrationOperations zkBioIntegrationService;

    @GetMapping("/status")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_DASHBOARD)")
    @Operation(summary = "Consulter le statut du serveur ZKBio", description = "Retourne l'état global du serveur ZKBio.")
    public ServiceStatusDTO getServerStatus() {
        log.info("GET /api/zkbio/status");
        return zkBioService.getServerStatus();
    }

    @GetMapping("/devices")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_DASHBOARD)")
    @Operation(summary = "Lister les équipements ZKBio", description = "Retourne les équipements détectés ou supervisés dans ZKBio.")
    public List<ServiceStatusDTO> getDevices() {
        log.info("GET /api/zkbio/devices");
        return zkBioService.fetchDevices();
    }

    @GetMapping("/problems")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_DASHBOARD)")
    @Operation(summary = "Lister les incidents ZKBio", description = "Retourne les incidents ou alertes remontés par ZKBio.")
    public List<ZkBioProblemDTO> getProblems() {
        log.info("GET /api/zkbio/problems");
        return zkBioService.fetchProblems();
    }

    @GetMapping("/attendance")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_DASHBOARD)")
    @Operation(summary = "Lister les pointages", description = "Retourne les journaux de présence disponibles dans ZKBio.")
    public List<ZkBioAttendanceDTO> getAttendanceLogs() {
        log.info("GET /api/zkbio/attendance");
        return zkBioService.fetchAttendanceLogs();
    }

    @GetMapping("/attendance/range")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_DASHBOARD)")
    @Operation(summary = "Lister les pointages sur une période", description = "Retourne les journaux de présence entre deux timestamps Unix.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pointages récupérés avec succès"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Paramètres de période invalides")
    })
    public List<ZkBioAttendanceDTO> getAttendanceLogsByRange(
            @Parameter(description = "Timestamp de début en secondes Unix", required = true)
            @RequestParam long startTime,
            @Parameter(description = "Timestamp de fin en secondes Unix", required = true)
            @RequestParam long endTime) {
        log.info("GET /api/zkbio/attendance/range?startTime={}&endTime={}", startTime, endTime);
        return zkBioService.fetchAttendanceLogs(startTime, endTime);
    }

    @PostMapping("/collect")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).REFRESH_DASHBOARD)")
    @Operation(summary = "Déclencher une collecte ZKBio", description = "Lance une collecte asynchrone complète ZKBio puis publie les snapshots.")
    public Mono<ResponseEntity<ApiResponse<Void>>> triggerCollection() {
        log.info("POST /api/zkbio/collect");
        return zkBioIntegrationService.refreshAllAndPublishAsync()
                .thenReturn(ResponseEntity.ok(
                        ApiResponse.<Void>builder()
                                .success(true)
                                .message("ZKBIO COLLECTED")
                                .source("SYSTEM")
                                .build()
                ));
    }

    @GetMapping("/users")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_DASHBOARD)")
    @Operation(summary = "Lister les utilisateurs ZKBio", description = "Retourne les utilisateurs synchronisés depuis ZKBio.")
    public List<ZkBioAttendanceDTO> getUsers() {
        log.info("GET /api/zkbio/users");
        return zkBioService.fetchUsers();
    }
}
