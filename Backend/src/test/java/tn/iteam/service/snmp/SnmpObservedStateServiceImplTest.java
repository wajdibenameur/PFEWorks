package tn.iteam.service.snmp;

import org.junit.jupiter.api.Test;
import tn.iteam.adapter.snmp.SnmpDeviceSnapshot;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.repository.SnmpDeviceRepository;
import tn.iteam.repository.SnmpInterfaceRepository;
import tn.iteam.domain.SnmpDevice;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SnmpObservedStateServiceImplTest {

    @Test
    void persistObservedStatus_skipsSnapshotsThatWereNotActuallyPolled() {
        SnmpDeviceRepository deviceRepository = mock(SnmpDeviceRepository.class);
        SnmpInterfaceRepository interfaceRepository = mock(SnmpInterfaceRepository.class);
        SnmpObservedStateServiceImpl service = new SnmpObservedStateServiceImpl(deviceRepository, interfaceRepository);

        SnmpDevice device = new SnmpDevice();
        device.setIpAddress("192.168.130.52");
        device.setHostname("hp-switch");
        device.setStatus(DeviceStatus.UP);
        device.setLastSeen(Instant.now());

        SnmpDeviceSnapshot snapshot = SnmpDeviceSnapshot.builder()
                .ipAddress("192.168.130.52")
                .hostId("192.168.130.52")
                .hostName("hp-switch")
                .status("UP")
                .deviceStatus(DeviceStatus.UP)
                .pollAttempted(false)
                .collectedAtEpochSec(Instant.now().getEpochSecond())
                .build();

        service.persistObservedStatus(List.of(device), List.of(snapshot));

        verify(deviceRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }
}
