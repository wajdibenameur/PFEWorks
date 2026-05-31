package tn.iteam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterfaceMetricsDTO {
    private Double inBandwidthMbps;
    private Double outBandwidthMbps;
    private Double utilizationPercent;
    private Long inOctets;
    private Long outOctets;
    private Long inErrors;
    private Long outErrors;
}
