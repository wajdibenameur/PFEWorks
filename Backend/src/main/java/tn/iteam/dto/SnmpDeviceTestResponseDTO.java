package tn.iteam.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class SnmpDeviceTestResponseDTO {

    private String ipAddress;
    private Integer snmpPort;
    private String snmpVersion;
    private String communityHint;
    private Boolean foundInInventory;
    private Boolean enabled;
    private String category;
    private String status;
    private String deviceStatus;
    private String hostName;
    private String sysDescr;
    private Long uptimeSeconds;
    private Double cpuPercent;
    private Double memoryPercent;
    private Integer interfaceCount;
    private Long durationMs;
    private Instant testedAt;
    private String diagnosticReason;
    private List<String> successfulOids;
    private List<String> failedOids;
}
