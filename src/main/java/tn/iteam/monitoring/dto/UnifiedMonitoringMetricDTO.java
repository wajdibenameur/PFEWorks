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
public class UnifiedMonitoringMetricDTO {
    private String id;
    private MonitoringSourceType source;
    private String hostId;
    private String hostName;
    private String itemId;
    private String metricName;
    private String metricKey;
    private Integer valueType;
    private String status;
    private String units;
    private Double value;
    private Long timestamp;
    private String ip;
    private Integer port;
}
