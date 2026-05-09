package tn.iteam.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.dto.CameraDeviceDTO;
import tn.iteam.service.CameraInventoryService;

import java.util.List;

@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
@PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_DASHBOARD)")
@Tag(name = "Caméras", description = "API d'inventaire des caméras")
public class CameraController {

    private final CameraInventoryService cameraInventoryService;

    @GetMapping
    @Operation(summary = "Lister les caméras enregistrées", description = "Retourne les caméras connues dans l'inventaire applicatif.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Caméras récupérées avec succès")
    })
    public List<CameraDeviceDTO> getRegisteredCameras() {
        return cameraInventoryService.getRegisteredCameras();
    }
}
