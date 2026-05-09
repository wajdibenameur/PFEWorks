package tn.iteam.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tn.iteam.dto.DashboardAnomalyDTO;
import tn.iteam.dto.DashboardOverviewDTO;
import tn.iteam.dto.DashboardPredictionDTO;
import tn.iteam.service.DashboardService;

import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(
        name = "app.db.enabled",
        havingValue = "false"
)
public class InMemoryDashboardService implements DashboardService {

    @Override
    public DashboardOverviewDTO getOverview() {
        return DashboardOverviewDTO.builder()
                .activeProblems(0)
                .problemsBySeverity(Map.of())
                .predictions(List.of())
                .anomalies(List.of())
                .dataQuality(Map.of())
                .warning("OFFLINE - Database not available")
                .build();
    }

    @Override
    public List<DashboardPredictionDTO> getPredictions() {
        return List.of();
    }

    @Override
    public List<DashboardAnomalyDTO> getAnomalies() {
        return List.of();
    }
}