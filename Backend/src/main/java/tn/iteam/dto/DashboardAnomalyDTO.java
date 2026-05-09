package tn.iteam.dto;

import lombok.Builder;

@Builder
public record DashboardAnomalyDTO(
        Long hostid,
        String hostName,
        String metricName,
        double anomalyScore,
        String status
) {
}
