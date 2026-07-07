package tn.iteam.adapter.snmp;

import lombok.Builder;
import lombok.Value;
import tn.iteam.enums.DeviceStatus;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class SnmpDeviceSnapshot {
    String ipAddress;
    String hostId;
    String hostName;
    String category;
    Integer snmpPort;
    String status;
    DeviceStatus deviceStatus;
    Double availability;
    Double cpuPercent;
    Double memoryPercent;
    Long uptimeSeconds;
    String sysDescr;
    List<SnmpInterfaceSnapshot> interfaces;
    Map<String, Double> extraMetrics;
    String diagnosticReason;
    List<String> successfulOids;
    List<String> failedOids;
    boolean pollAttempted;
    long collectedAtEpochSec;
}
