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
import tn.iteam.service.ObserviumSummaryService;

import java.util.Map;

@RestController
@RequestMapping("/api/observium")
@RequiredArgsConstructor
@PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_DASHBOARD)")
/**
 * Temporary compatibility controller for Observium-specific API consumers.
 *
 * Keep this endpoint until frontend and external consumers are confirmed to be
 * fully migrated away from {@code /api/observium/*}. The summary is already
 * computed from the unified monitoring aggregation flow.
 *
 * Once compatibility is no longer needed, this controller can be moved to
 * {@code depl}.
 */
@Tag(name = "Observium", description = "Endpoints de compatibilité pour Observium")
public class ObserviumController {

    private final ObserviumSummaryService observiumSummaryService;

    @GetMapping("/summary")
    @Operation(summary = "Consulter le résumé Observium", description = "Retourne un résumé agrégé des données Observium.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Résumé Observium récupéré avec succès")
    })
    public Map<String, Long> getSummary() {
        return observiumSummaryService.getSummary();
    }
}
