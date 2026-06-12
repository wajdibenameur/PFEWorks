package tn.iteam.service;

import tn.iteam.adapter.snmp.SnmpDeviceSnapshot;
import tn.iteam.domain.SnmpDevice;

import java.util.List;

public interface SnmpMonitoringService {

    List<SnmpDeviceSnapshot> pollEnabledDevices();

    SnmpDeviceSnapshot pollSingleDevice(SnmpDevice device);
}
