package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
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
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public DashboardOverviewDTO overview() {
        return dashboardService.getOverview();
    }

    @GetMapping("/predictions")
    public List<DashboardPredictionDTO> predictions() {
        return dashboardService.getPredictions();
    }

    @GetMapping("/anomalies")
    public List<DashboardAnomalyDTO> anomalies() {
        return dashboardService.getAnomalies();
    }
}
