package tn.iteam.adapter.observium;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.service.observium.ObserviumSnmpPollingService;
import tn.iteam.util.MonitoringConstants;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObserviumAdapterSnmpTest {

    @Mock
    private ObserviumSnmpPollingService pollingService;

    @InjectMocks
    private ObserviumAdapter adapter;

    @Test
    void fetchProblems_createsProblemsOnlyForDownOrDegraded() {
        when(pollingService.pollEnabledDevices()).thenReturn(List.of(
                snapshot("10.0.0.1", "host-up", DeviceStatus.UP, MonitoringConstants.STATUS_UP, 1.0, 33.0, 44.0, 3600L),
                snapshot("10.0.0.2", "host-down", DeviceStatus.DOWN, MonitoringConstants.STATUS_DOWN, 0.0, null, null, null),
                snapshot("10.0.0.3", "host-degraded", DeviceStatus.DEGRADED, MonitoringConstants.STATUS_DEGRADED, 0.5, 12.0, null, null)
        ));

        var problems = adapter.fetchProblems();

        assertThat(problems).hasSize(2);
        assertThat(problems).allMatch(p -> p.isActive());
        assertThat(problems).extracting("hostId").containsExactlyInAnyOrder("10.0.0.2", "10.0.0.3");
        assertThat(problems).filteredOn(p -> "10.0.0.2".equals(p.getHostId()))
                .singleElement()
                .satisfies(problem -> assertThat(problem.getSeverity()).isEqualTo("4"));
        assertThat(problems).filteredOn(p -> "10.0.0.3".equals(p.getHostId()))
                .singleElement()
                .satisfies(problem -> assertThat(problem.getSeverity()).isEqualTo("3"));
    }

    @Test
    void fetchMetrics_exposesAvailabilityAndOptionalCpuMemoryUptime() {
        when(pollingService.pollEnabledDevices()).thenReturn(List.of(
                snapshot("10.0.0.10", "host-full", DeviceStatus.UP, MonitoringConstants.STATUS_UP, 1.0, 51.0, 68.0, 1000L),
                snapshot("10.0.0.11", "host-min", DeviceStatus.DOWN, MonitoringConstants.STATUS_DOWN, 0.0, null, null, null)
        ));

        var metrics = adapter.fetchMetrics();

        assertThat(metrics.stream().filter(m -> "observium.snmp.availability".equals(m.getMetricKey())).count()).isEqualTo(2);
        assertThat(metrics.stream().filter(m -> "observium.snmp.cpu.percent".equals(m.getMetricKey())).count()).isEqualTo(1);
        assertThat(metrics.stream().filter(m -> "observium.snmp.memory.percent".equals(m.getMetricKey())).count()).isEqualTo(1);
        assertThat(metrics.stream().filter(m -> "observium.snmp.uptime.seconds".equals(m.getMetricKey())).count()).isEqualTo(1);
    }

    @Test
    void fetchAll_mapsStatusAndProtocolToSnmpHostSnapshot() {
        when(pollingService.pollEnabledDevices()).thenReturn(List.of(
                snapshot("192.168.1.10", "edge-sw", DeviceStatus.DEGRADED, MonitoringConstants.STATUS_DEGRADED, 0.5, null, null, null)
        ));

        var hosts = adapter.fetchAll();

        assertThat(hosts).hasSize(1);
        assertThat(hosts.get(0).getSource()).isEqualTo(MonitoringConstants.SOURCE_OBSERVIUM);
        assertThat(hosts.get(0).getProtocol()).isEqualTo("SNMP");
        assertThat(hosts.get(0).getStatus()).isEqualTo(MonitoringConstants.STATUS_DEGRADED);
        assertThat(hosts.get(0).getIp()).isEqualTo("192.168.1.10");
    }

    private ObserviumSnmpDeviceSnapshot snapshot(
            String ip,
            String hostName,
            DeviceStatus deviceStatus,
            String status,
            Double availability,
            Double cpu,
            Double memory,
            Long uptime
    ) {
        return ObserviumSnmpDeviceSnapshot.builder()
                .ipAddress(ip)
                .hostId(ip)
                .hostName(hostName)
                .snmpPort(161)
                .deviceStatus(deviceStatus)
                .status(status)
                .availability(availability)
                .cpuPercent(cpu)
                .memoryPercent(memory)
                .uptimeSeconds(uptime)
                .collectedAtEpochSec(System.currentTimeMillis() / 1000)
                .build();
    }
}

