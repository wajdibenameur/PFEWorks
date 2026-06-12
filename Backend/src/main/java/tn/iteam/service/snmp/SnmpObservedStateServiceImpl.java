package tn.iteam.service.snmp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.snmp.SnmpDeviceSnapshot;
import tn.iteam.adapter.snmp.SnmpInterfaceSnapshot;
import tn.iteam.domain.SnmpDevice;
import tn.iteam.domain.SnmpInterface;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.repository.SnmpDeviceRepository;
import tn.iteam.repository.SnmpInterfaceRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SnmpObservedStateServiceImpl implements tn.iteam.service.SnmpObservedStateService {

    private final SnmpDeviceRepository deviceRepository;
    private final SnmpInterfaceRepository interfaceRepository;

    @Override
    public Map<Integer, SnmpInterface> loadPreviousInterfacesByIndex(String hostId) {
        if (hostId == null || hostId.isBlank()) {
            return Map.of();
        }
        return interfaceRepository.findByHostIdIn(List.of(hostId)).stream()
                .collect(Collectors.toMap(SnmpInterface::getIfIndex, existing -> existing, (left, right) -> left));
    }

    @Override
    public void persistObservedStatus(List<SnmpDevice> devices, List<SnmpDeviceSnapshot> snapshots) {
        Map<String, SnmpDeviceSnapshot> byIp = snapshots.stream()
                .collect(Collectors.toMap(SnmpDeviceSnapshot::getIpAddress, item -> item, (left, right) -> left));
        List<SnmpDevice> toSave = new ArrayList<>(devices.size());
        for (SnmpDevice device : devices) {
            SnmpDeviceSnapshot snapshot = byIp.get(device.getIpAddress());
            if (snapshot == null) {
                continue;
            }
            Instant polledAt = snapshot.getCollectedAtEpochSec() > 0
                    ? Instant.ofEpochSecond(snapshot.getCollectedAtEpochSec())
                    : Instant.now();
            device.setHostname(snapshot.getHostName());
            device.setStatus(snapshot.getDeviceStatus());
            device.setLastPolledAt(polledAt);
            device.setLastSeen(snapshot.getDeviceStatus() == DeviceStatus.DOWN ? device.getLastSeen() : Instant.now());
            if (snapshot.getDeviceStatus() == DeviceStatus.DOWN) {
                device.setLastFailureAt(polledAt);
                device.setFailureCount((device.getFailureCount() != null ? device.getFailureCount() : 0) + 1);
            } else {
                device.setLastSuccessAt(polledAt);
                device.setFailureCount(0);
            }
            toSave.add(device);
        }
        if (!toSave.isEmpty()) {
            deviceRepository.saveAll(toSave);
        }
    }

    @Override
    public void persistInterfaces(List<SnmpDeviceSnapshot> snapshots) {
        List<SnmpInterface> entitiesToSave = new ArrayList<>();
        for (SnmpDeviceSnapshot snapshot : snapshots) {
            List<SnmpInterfaceSnapshot> interfaces = snapshot.getInterfaces();
            if (interfaces == null || interfaces.isEmpty()) {
                continue;
            }
            Map<Integer, SnmpInterface> existingByIfIndex = loadPreviousInterfacesByIndex(snapshot.getHostId());

            for (SnmpInterfaceSnapshot iface : interfaces) {
                SnmpInterface entity = existingByIfIndex.getOrDefault(iface.getIfIndex(), new SnmpInterface());
                entity.setHostId(snapshot.getHostId());
                entity.setIpAddress(snapshot.getIpAddress());
                entity.setIfIndex(iface.getIfIndex());
                entity.setName(iface.getName());
                entity.setAdminStatus(iface.getAdminStatus());
                entity.setOperStatus(iface.getOperStatus());
                entity.setInOctets(iface.getInOctets());
                entity.setOutOctets(iface.getOutOctets());
                entity.setInErrors(iface.getInErrors());
                entity.setOutErrors(iface.getOutErrors());
                entity.setSpeedBps(iface.getSpeedBps());
                entity.setInBandwidthMbps(iface.getInBandwidthMbps());
                entity.setOutBandwidthMbps(iface.getOutBandwidthMbps());
                entity.setUtilizationPercent(iface.getUtilizationPercent());
                entity.setLastPollEpochSec(snapshot.getCollectedAtEpochSec());
                entitiesToSave.add(entity);
            }
        }
        if (!entitiesToSave.isEmpty()) {
            interfaceRepository.saveAll(entitiesToSave);
        }
    }
}
