package tn.iteam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CameraDeviceDTO {

    private Long id;
    private String source;
    private String name;
    private String site;
    private String type;
    private String ip;
    private Integer port;
    private String protocol;
    private String status;
    private String category;
    private LocalDateTime lastScanAt;
    private boolean reachable;
    private boolean persisted;
    private boolean snmpEnabled;
    private String snmpStatus;
    private String snmpSysName;
    private LocalDateTime snmpLastSeenAt;
    private Long snmpUptimeSeconds;
    private Double snmpCpuPercent;
    private Double snmpMemoryPercent;
    private Integer snmpInterfaceCount;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
