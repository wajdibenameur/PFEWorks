package tn.iteam.adapter.snmp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.adapter.snmp.SnmpInterfaceSnapshot;
import tn.iteam.dto.SnmpProblemDTO;
import tn.iteam.service.SnmpMonitoringService;
import tn.iteam.service.snmp.SnmpSubnetClassifier;
import tn.iteam.util.MonitoringConstants;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnmpAdapterTest {

    @Mock
    private SnmpMonitoringService pollingService;
    @Mock
    private SnmpSubnetClassifier subnetClassifier;

    @InjectMocks
    private SnmpAdapter adapter;

    @Test
    void fetchProblems_createsProblemsOnlyForDownOrDegraded() {
        stubClassifierFor("10.0.0.1");
        stubClassifierFor("10.0.0.2");
        stubClassifierFor("10.0.0.3");
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
    void fetchProblems_createsDerivedMetricProblemsForCriticalSnmpConditions() {
        stubClassifierFor("10.0.0.50");
        when(pollingService.pollEnabledDevices()).thenReturn(List.of(
                snapshot(
                        "10.0.0.50",
                        "ups-printer-edge",
                        DeviceStatus.UP,
                        MonitoringConstants.STATUS_UP,
                        1.0,
                        null,
                        null,
                        500L,
                        Map.of(
                                "ups.battery.percent", 12.0,
                                "ups.output.load.percent", 96.0,
                                "printer.toner.percent", 9.0
                        ),
                        List.of(SnmpInterfaceSnapshot.builder()
                                .ifIndex(7)
                                .name("uplink-7")
                                .utilizationPercent(97.0)
                                .recentInErrors(3L)
                                .build())
                )
        ));

        var problems = adapter.fetchProblems();

        assertThat(problems).extracting(SnmpProblemDTO::getProblemId)
                .containsExactlyInAnyOrder(
                        "OBS-SNMP-10.0.0.50-interface-errors-7",
                        "OBS-SNMP-10.0.0.50-interface-utilization-7",
                        "OBS-SNMP-10.0.0.50-printer-toner-low",
                        "OBS-SNMP-10.0.0.50-ups-battery-low",
                        "OBS-SNMP-10.0.0.50-ups-load-high"
                );
        assertThat(problems).extracting(SnmpProblemDTO::getSeverity)
                .containsExactlyInAnyOrder("5", "5", "5", "4", "4");
    }

    @Test
    void fetchProblems_createsServerAndFirewallMetricProblems() {
        when(subnetClassifier.isIncludedInScope("10.0.0.60")).thenReturn(true);
        when(subnetClassifier.resolveCategory("10.0.0.60")).thenReturn(MonitoringConstants.CATEGORY_SERVER);
        when(subnetClassifier.isIncludedInScope("10.0.0.61")).thenReturn(true);
        when(subnetClassifier.resolveCategory("10.0.0.61")).thenReturn(MonitoringConstants.CATEGORY_FIREWALL);
        when(pollingService.pollEnabledDevices()).thenReturn(List.of(
                snapshot("10.0.0.60", "srv-app", DeviceStatus.UP, MonitoringConstants.STATUS_UP, 1.0, 96.0, 91.0, 200L),
                snapshot("10.0.0.61", "fw-edge", DeviceStatus.UP, MonitoringConstants.STATUS_UP, 1.0, 86.0, 97.0, 200L)
        ));

        var problems = adapter.fetchProblems();

        assertThat(problems).extracting(SnmpProblemDTO::getProblemId)
                .containsExactlyInAnyOrder(
                        "OBS-SNMP-10.0.0.60-cpu-high",
                        "OBS-SNMP-10.0.0.60-memory-high",
                        "OBS-SNMP-10.0.0.61-cpu-high",
                        "OBS-SNMP-10.0.0.61-memory-high"
                );
        assertThat(problems).extracting(SnmpProblemDTO::getSeverity)
                .containsExactlyInAnyOrder("5", "4", "4", "5");
    }

    @Test
    void fetchMetrics_exposesAvailabilityAndOptionalCpuMemoryUptime() {
        stubClassifierFor("10.0.0.10");
        stubClassifierFor("10.0.0.11");
        when(pollingService.pollEnabledDevices()).thenReturn(List.of(
                snapshot("10.0.0.10", "host-full", DeviceStatus.UP, MonitoringConstants.STATUS_UP, 1.0, 51.0, 68.0, 1000L,
                        Map.of("printer.toner.percent", 42.0, "printer.pages.total", 12345.0)),
                snapshot("10.0.0.11", "host-min", DeviceStatus.DOWN, MonitoringConstants.STATUS_DOWN, 0.0, null, null, null)
        ));

        var metrics = adapter.fetchMetrics();

        assertThat(metrics.stream().filter(m -> "snmp.availability".equals(m.getMetricKey())).count()).isEqualTo(2);
        assertThat(metrics.stream().filter(m -> "snmp.cpu.percent".equals(m.getMetricKey())).count()).isEqualTo(1);
        assertThat(metrics.stream().filter(m -> "snmp.memory.percent".equals(m.getMetricKey())).count()).isEqualTo(1);
        assertThat(metrics.stream().filter(m -> "snmp.uptime.seconds".equals(m.getMetricKey())).count()).isEqualTo(1);
        assertThat(metrics.stream().filter(m -> "snmp.printer.toner.percent".equals(m.getMetricKey())).count()).isEqualTo(1);
        assertThat(metrics.stream().filter(m -> "snmp.printer.pages.total".equals(m.getMetricKey())).count()).isEqualTo(1);
    }

    @Test
    void fetchAll_mapsStatusAndProtocolToSnmpHostSnapshot() {
        stubClassifierFor("192.168.1.10");
        when(pollingService.pollEnabledDevices()).thenReturn(List.of(
                snapshot("192.168.1.10", "edge-sw", DeviceStatus.DEGRADED, MonitoringConstants.STATUS_DEGRADED, 0.5, null, null, null)
        ));

        var hosts = adapter.fetchAll();

        assertThat(hosts).hasSize(1);
        assertThat(hosts.get(0).getSource()).isEqualTo(MonitoringConstants.SOURCE_SNMP);
        assertThat(hosts.get(0).getProtocol()).isEqualTo("SNMP");
        assertThat(hosts.get(0).getStatus()).isEqualTo(MonitoringConstants.STATUS_DEGRADED);
        assertThat(hosts.get(0).getIp()).isEqualTo("192.168.1.10");
    }

    private SnmpDeviceSnapshot snapshot(
            String ip,
            String hostName,
            DeviceStatus deviceStatus,
            String status,
            Double availability,
            Double cpu,
            Double memory,
            Long uptime
    ) {
        return snapshot(ip, hostName, deviceStatus, status, availability, cpu, memory, uptime, Map.of());
    }

    private SnmpDeviceSnapshot snapshot(
            String ip,
            String hostName,
            DeviceStatus deviceStatus,
            String status,
            Double availability,
            Double cpu,
            Double memory,
            Long uptime,
            Map<String, Double> extraMetrics
    ) {
        return snapshot(ip, hostName, deviceStatus, status, availability, cpu, memory, uptime, extraMetrics, List.of());
    }

    private SnmpDeviceSnapshot snapshot(
            String ip,
            String hostName,
            DeviceStatus deviceStatus,
            String status,
            Double availability,
            Double cpu,
            Double memory,
            Long uptime,
            Map<String, Double> extraMetrics,
            List<SnmpInterfaceSnapshot> interfaces
    ) {
        return SnmpDeviceSnapshot.builder()
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
                .interfaces(interfaces)
                .extraMetrics(extraMetrics)
                .collectedAtEpochSec(System.currentTimeMillis() / 1000)
                .build();
    }

    private void stubClassifierFor(String ip) {
        when(subnetClassifier.isIncludedInScope(ip)).thenReturn(true);
        when(subnetClassifier.resolveCategory(ip)).thenReturn(MonitoringConstants.CATEGORY_SWITCH);
    }
}
