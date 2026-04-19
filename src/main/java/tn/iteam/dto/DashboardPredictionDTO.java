package tn.iteam.dto;

import lombok.Builder;

@Builder
public record DashboardPredictionDTO(
        Long hostid,
        String hostName,
        int prediction,
        double probability,
        String status
) {
}
