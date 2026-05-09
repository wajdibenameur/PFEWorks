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
public class UnifiedMonitoringProblemDTO {
    private String id;
    private MonitoringSourceType source;
    private String problemId;
    private Long eventId;
    private String hostId;
    private String hostName;
    private String description;
    private String severity;
    private boolean active;
    private String status;
    private String ip;
    private Integer port;
    private Long startedAt;
    private String startedAtFormatted;
    private Long resolvedAt;
    private String resolvedAtFormatted;
}
