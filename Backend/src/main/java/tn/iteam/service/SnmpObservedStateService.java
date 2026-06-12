package tn.iteam.service;

import tn.iteam.adapter.snmp.SnmpDeviceSnapshot;
import tn.iteam.domain.SnmpDevice;
import tn.iteam.domain.SnmpInterface;

import java.util.List;
import java.util.Map;

public interface SnmpObservedStateService {

    Map<Integer, SnmpInterface> loadPreviousInterfacesByIndex(String hostId);

    void persistObservedStatus(List<SnmpDevice> devices, List<SnmpDeviceSnapshot> snapshots);

    void persistInterfaces(List<SnmpDeviceSnapshot> snapshots);
}
