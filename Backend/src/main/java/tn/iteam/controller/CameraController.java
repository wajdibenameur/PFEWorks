package tn.iteam.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.dto.CameraDeviceDTO;
import tn.iteam.dto.CameraDeviceUpsertRequest;
import tn.iteam.service.CameraInventoryService;
import tn.iteam.service.camera.CameraDeviceManagementService;

import java.util.List;

@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
@PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_CAMERA)")
@Tag(name = "Caméras", description = "API d'inventaire des caméras")
public class CameraController {

    private final CameraInventoryService cameraInventoryService;
    private final CameraDeviceManagementService cameraDeviceManagementService;

    @GetMapping
    @Operation(summary = "Lister les caméras enregistrées", description = "Retourne les caméras connues dans l'inventaire applicatif.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Caméras récupérées avec succès")
    })
    public List<CameraDeviceDTO> getRegisteredCameras() {
        return cameraInventoryService.getRegisteredCameras();
    }

    @PostMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).MANAGE_HOSTS)")
    @Operation(summary = "Ajouter une caméra")
    public ResponseEntity<tn.iteam.domain.ApiResponse<CameraDeviceDTO>> createCamera(
            @Valid @RequestBody CameraDeviceUpsertRequest request
    ) {
        CameraDeviceDTO device = cameraDeviceManagementService.createDevice(request);
        return ResponseEntity.ok(tn.iteam.domain.ApiResponse.<CameraDeviceDTO>builder()
                .success(true)
                .message("CAMERA CREATED")
                .source("CAMERA")
                .data(device)
                .build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).MANAGE_HOSTS)")
    @Operation(summary = "Modifier une caméra")
    public ResponseEntity<tn.iteam.domain.ApiResponse<CameraDeviceDTO>> updateCamera(
            @PathVariable Long id,
            @Valid @RequestBody CameraDeviceUpsertRequest request
    ) {
        CameraDeviceDTO device = cameraDeviceManagementService.updateDevice(id, request);
        return ResponseEntity.ok(tn.iteam.domain.ApiResponse.<CameraDeviceDTO>builder()
                .success(true)
                .message("CAMERA UPDATED")
                .source("CAMERA")
                .data(device)
                .build());
    }

    @PatchMapping("/{id}/enable")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).MANAGE_HOSTS)")
    @Operation(summary = "Activer une caméra")
    public ResponseEntity<tn.iteam.domain.ApiResponse<CameraDeviceDTO>> enableCamera(@PathVariable Long id) {
        return enabledResponse(id, true);
    }

    @PatchMapping("/{id}/disable")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).MANAGE_HOSTS)")
    @Operation(summary = "Désactiver une caméra")
    public ResponseEntity<tn.iteam.domain.ApiResponse<CameraDeviceDTO>> disableCamera(@PathVariable Long id) {
        return enabledResponse(id, false);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).MANAGE_HOSTS)")
    @Operation(summary = "Supprimer une caméra")
    public ResponseEntity<tn.iteam.domain.ApiResponse<Void>> deleteCamera(@PathVariable Long id) {
        cameraDeviceManagementService.deleteDevice(id);
        return ResponseEntity.ok(tn.iteam.domain.ApiResponse.<Void>builder()
                .success(true)
                .message("CAMERA DELETED")
                .source("CAMERA")
                .build());
    }

    private ResponseEntity<tn.iteam.domain.ApiResponse<CameraDeviceDTO>> enabledResponse(Long id, boolean enabled) {
        CameraDeviceDTO device = cameraDeviceManagementService.updateEnabled(id, enabled);
        return ResponseEntity.ok(tn.iteam.domain.ApiResponse.<CameraDeviceDTO>builder()
                .success(true)
                .message(enabled ? "CAMERA ENABLED" : "CAMERA DISABLED")
                .source("CAMERA")
                .data(device)
                .build());
    }
}
