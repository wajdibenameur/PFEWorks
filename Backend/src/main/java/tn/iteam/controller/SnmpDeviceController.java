package tn.iteam.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.domain.ApiResponse;
import tn.iteam.dto.SnmpDeviceCreateRequest;
import tn.iteam.dto.SnmpDeviceMetricsUpdateRequest;
import tn.iteam.dto.SnmpDeviceResponseDTO;
import tn.iteam.service.snmp.SnmpDeviceManagementService;

import java.util.List;

@RestController
@RequestMapping({"/api/monitoring/snmp/devices", "/api/devices"})
@RequiredArgsConstructor
@Tag(name = "SNMP Devices", description = "Gestion manuelle des equipements SNMP suivis")
public class SnmpDeviceController {

    private final SnmpDeviceManagementService deviceManagementService;

    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_HOSTS)")
    @Operation(summary = "Lister les equipements SNMP suivis")
    public List<SnmpDeviceResponseDTO> listDevices() {
        return deviceManagementService.listDevices();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_HOSTS)")
    @Operation(summary = "Afficher un equipement SNMP")
    public SnmpDeviceResponseDTO getDevice(@PathVariable Long id) {
        return deviceManagementService.getDevice(id);
    }

    @PostMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).MANAGE_HOSTS)")
    @Operation(summary = "Ajouter un equipement SNMP a suivre")
    public ResponseEntity<ApiResponse<SnmpDeviceResponseDTO>> createDevice(@Valid @RequestBody SnmpDeviceCreateRequest request) {
        SnmpDeviceResponseDTO device = deviceManagementService.createDevice(request);
        return ResponseEntity.ok(ApiResponse.<SnmpDeviceResponseDTO>builder()
                .success(true)
                .message("SNMP DEVICE CREATED")
                .source("SNMP")
                .data(device)
                .build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).MANAGE_HOSTS)")
    @Operation(summary = "Modifier un equipement SNMP")
    public ResponseEntity<ApiResponse<SnmpDeviceResponseDTO>> updateDevice(
            @PathVariable Long id,
            @Valid @RequestBody SnmpDeviceCreateRequest request
    ) {
        SnmpDeviceResponseDTO device = deviceManagementService.updateDevice(id, request);
        return ResponseEntity.ok(ApiResponse.<SnmpDeviceResponseDTO>builder()
                .success(true)
                .message("SNMP DEVICE UPDATED")
                .source("SNMP")
                .data(device)
                .build());
    }

    @PatchMapping("/{id}/enable")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).MANAGE_HOSTS)")
    @Operation(summary = "Activer un equipement SNMP")
    public ResponseEntity<ApiResponse<SnmpDeviceResponseDTO>> enableDevice(@PathVariable Long id) {
        return enabledResponse(id, true);
    }

    @PatchMapping("/{id}/disable")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).MANAGE_HOSTS)")
    @Operation(summary = "Desactiver un equipement SNMP")
    public ResponseEntity<ApiResponse<SnmpDeviceResponseDTO>> disableDevice(@PathVariable Long id) {
        return enabledResponse(id, false);
    }

    @PatchMapping("/{id}/enabled")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).MANAGE_HOSTS)")
    @Operation(summary = "Activer ou desactiver un equipement SNMP")
    public ResponseEntity<ApiResponse<SnmpDeviceResponseDTO>> updateEnabled(
            @PathVariable Long id,
            @RequestParam boolean enabled
    ) {
        return enabledResponse(id, enabled);
    }

    @PutMapping("/{id}/metrics")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).MANAGE_HOSTS)")
    @Operation(summary = "Modifier les metriques suivies d'un equipement SNMP")
    public ResponseEntity<ApiResponse<SnmpDeviceResponseDTO>> updateMetrics(
            @PathVariable Long id,
            @Valid @RequestBody SnmpDeviceMetricsUpdateRequest request
    ) {
        SnmpDeviceResponseDTO device = deviceManagementService.updateMetrics(id, request);
        return ResponseEntity.ok(ApiResponse.<SnmpDeviceResponseDTO>builder()
                .success(true)
                .message("SNMP DEVICE METRICS UPDATED")
                .source("SNMP")
                .data(device)
                .build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).MANAGE_HOSTS)")
    @Operation(summary = "Supprimer ou desactiver un equipement SNMP")
    public ResponseEntity<ApiResponse<Void>> deleteDevice(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean hardDelete
    ) {
        deviceManagementService.deleteDevice(id, hardDelete);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message(hardDelete ? "SNMP DEVICE DELETED" : "SNMP DEVICE DISABLED")
                .source("SNMP")
                .build());
    }

    private ResponseEntity<ApiResponse<SnmpDeviceResponseDTO>> enabledResponse(Long id, boolean enabled) {
        SnmpDeviceResponseDTO device = deviceManagementService.updateEnabled(id, enabled);
        return ResponseEntity.ok(ApiResponse.<SnmpDeviceResponseDTO>builder()
                .success(true)
                .message(enabled ? "SNMP DEVICE ENABLED" : "SNMP DEVICE DISABLED")
                .source("SNMP")
                .data(device)
                .build());
    }
}
