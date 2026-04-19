package tn.iteam.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tn.iteam.monitoring.MonitoringSourceType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedMonitoringHostDTO {
    private String id;
    private MonitoringSourceType source;
    private String hostId;
    private String name;
    private String ip;
    private Integer port;
    private String protocol;
    private String status;
    private String category;
}
