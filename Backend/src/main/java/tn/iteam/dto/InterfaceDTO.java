package tn.iteam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterfaceDTO {
    private String hostId;
    private String ipAddress;
    private Integer ifIndex;
    private String name;
    private String adminStatus;
    private String operStatus;
    private Long speedBps;
    private InterfaceMetricsDTO metrics;
}
