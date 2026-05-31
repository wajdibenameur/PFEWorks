package tn.iteam.adapter.observium;

import lombok.Builder;
import lombok.Value;
import tn.iteam.enums.DeviceStatus;

import java.util.List;

@Value
@Builder
public class ObserviumSnmpDeviceSnapshot {
    String ipAddress;
    String hostId;
    String hostName;
    Integer snmpPort;
    String status;
    DeviceStatus deviceStatus;
    Double availability;
    Double cpuPercent;
    Double memoryPercent;
    Long uptimeSeconds;
    String sysDescr;
    List<ObserviumInterfaceSnapshot> interfaces;
    long collectedAtEpochSec;
}
