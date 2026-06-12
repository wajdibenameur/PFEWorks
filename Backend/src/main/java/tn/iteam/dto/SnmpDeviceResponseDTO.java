package tn.iteam.dto;

import lombok.Builder;
import lombok.Value;
import tn.iteam.enums.SnmpDeviceType;

import java.time.Instant;
import java.util.Set;

@Value
@Builder
public class SnmpDeviceResponseDTO {
    Long id;
    String ipAddress;
    String hostname;
    SnmpDeviceType type;
    String category;
    String deviceGroup;
    Integer snmpPort;
    String snmpCommunity;
    String snmpVersion;
    Integer pollingIntervalSeconds;
    Set<String> metricsToPoll;
    String status;
    Instant lastSeen;
    Instant lastPolledAt;
    Instant lastSuccessAt;
    Instant lastFailureAt;
    Integer failureCount;
    Instant createdAt;
    Instant updatedAt;
    Boolean enabled;
    Boolean manualEntry;
}
