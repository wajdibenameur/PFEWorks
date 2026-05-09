package tn.iteam.dto;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record DashboardOverviewDTO(
        long activeProblems,
        Map<String, Long> problemsBySeverity,
        List<DashboardPredictionDTO> predictions,
        List<DashboardAnomalyDTO> anomalies,
        Map<String, Object> dataQuality,
        String warning
) {
}
