package tn.iteam.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.dto.DashboardAnomalyDTO;
import tn.iteam.dto.DashboardOverviewDTO;
import tn.iteam.dto.DashboardPredictionDTO;
import tn.iteam.service.DashboardService;

import java.util.List;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "API de données synthétiques pour le tableau de bord")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_DASHBOARD)")
    @Operation(summary = "Consulter la vue d'ensemble", description = "Retourne les indicateurs synthétiques du tableau de bord.")
    public DashboardOverviewDTO overview() {
        return dashboardService.getOverview();
    }

    @GetMapping("/predictions")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_DASHBOARD)")
    @Operation(summary = "Lister les prédictions", description = "Retourne les prédictions affichées dans le tableau de bord.")
    public List<DashboardPredictionDTO> predictions() {
        return dashboardService.getPredictions();
    }

    @GetMapping("/anomalies")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_DASHBOARD)")
    @Operation(summary = "Lister les anomalies", description = "Retourne les anomalies détectées pour le tableau de bord.")
    public List<DashboardAnomalyDTO> anomalies() {
        return dashboardService.getAnomalies();
    }
}
