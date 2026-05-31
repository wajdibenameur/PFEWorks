package tn.iteam.adapter.observium;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tn.iteam.adapter.observium.ObserviumInterfaceSnapshot;
import tn.iteam.dto.ObserviumMetricDTO;
import tn.iteam.dto.ObserviumProblemDTO;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.service.observium.ObserviumSubnetClassifier;
import tn.iteam.service.observium.ObserviumSnmpPollingService;
import tn.iteam.util.MonitoringConstants;

import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ObserviumAdapter {

    private final ObserviumSnmpPollingService snmpPollingService;
    private final ObserviumSubnetClassifier subnetClassifier;
    private volatile List<ObserviumSnmpDeviceSnapshot> lastSnapshots = List.of();
    private volatile long lastPollEpochMs = 0L;
    private static final long SNAPSHOT_CACHE_MS = 2_000L;

    private List<ObserviumSnmpDeviceSnapshot> pollSnapshots() {
        long now = System.currentTimeMillis();
        if (now - lastPollEpochMs <= SNAPSHOT_CACHE_MS && !lastSnapshots.isEmpty()) {
            return lastSnapshots;
        }
        synchronized (this) {
            long refreshedNow = System.currentTimeMillis();
            if (refreshedNow - lastPollEpochMs <= SNAPSHOT_CACHE_MS && !lastSnapshots.isEmpty()) {
                return lastSnapshots;
            }
            lastSnapshots = snmpPollingService.pollEnabledDevices();
            lastPollEpochMs = refreshedNow;
            return lastSnapshots;
        }
    }

    public List<ServiceStatusDTO> fetchAll() {
        List<ObserviumSnmpDeviceSnapshot> snapshots = pollSnapshots();
        return snapshots.stream()
                .filter(snapshot -> subnetClassifier.isIncludedInScope(snapshot.getIpAddress()))
                .filter(snapshot -> isManagedObserviumCategory(snapshot.getIpAddress()))
                .map(snapshot -> ServiceStatusDTO.builder()
                        .source(MonitoringConstants.SOURCE_OBSERVIUM)
                        .hostId(snapshot.getHostId())
                        .name(snapshot.getHostName())
                        .ip(snapshot.getIpAddress())
                        .port(snapshot.getSnmpPort())
                        .protocol("SNMP")
                        .status(snapshot.getStatus())
                        .category(subnetClassifier.resolveCategory(snapshot.getIpAddress()))
                        .lastCheck(java.time.LocalDateTime.now())
                        .build())
                .toList();
    }

    public List<ObserviumProblemDTO> fetchProblems() {
        long now = Instant.now().getEpochSecond();
        List<ObserviumSnmpDeviceSnapshot> snapshots = pollSnapshots();
        List<ObserviumProblemDTO> problems = new ArrayList<>();

        for (ObserviumSnmpDeviceSnapshot snapshot : snapshots) {
            if (!subnetClassifier.isIncludedInScope(snapshot.getIpAddress())) {
                continue;
            }
            if (!isManagedObserviumCategory(snapshot.getIpAddress())) {
                continue;
            }
            if (MonitoringConstants.STATUS_UP.equalsIgnoreCase(snapshot.getStatus())) {
                continue;
            }

            String severity = MonitoringConstants.STATUS_DOWN.equalsIgnoreCase(snapshot.getStatus()) ? "4" : "3";
            problems.add(ObserviumProblemDTO.builder()
                    .problemId("OBS-SNMP-" + snapshot.getIpAddress())
                    .host(snapshot.getHostName())
                    .hostId(snapshot.getHostId())
                    .description("Observium SNMP device status is " + snapshot.getStatus())
                    .severity(severity)
                    .active(true)
                    .source(MonitoringConstants.SOURCE_OBSERVIUM)
                    .eventId(now)
                    .startedAt(now)
                    .resolvedAt(null)
                    .build());
        }
        return problems;
    }

    public List<ObserviumMetricDTO> fetchMetrics() {
        List<ObserviumSnmpDeviceSnapshot> snapshots = pollSnapshots();
        long now = Instant.now().getEpochSecond();
        List<ObserviumMetricDTO> metrics = new ArrayList<>();

        for (ObserviumSnmpDeviceSnapshot snapshot : snapshots) {
            if (!subnetClassifier.isIncludedInScope(snapshot.getIpAddress())) {
                continue;
            }
            if (!isManagedObserviumCategory(snapshot.getIpAddress())) {
                continue;
            }
            String hostName = snapshot.getHostName() != null && !snapshot.getHostName().isBlank() ? snapshot.getHostName() : "UNKNOWN";
            String hostId = snapshot.getHostId();

            metrics.add(ObserviumMetricDTO.builder()
                    .hostId(hostId)
                    .hostName(hostName)
                    .itemId("availability")
                    .metricKey("observium.snmp.availability")
                    .value(snapshot.getAvailability())
                    .timestamp(now)
                    .ip(snapshot.getIpAddress())
                    .port(snapshot.getSnmpPort())
                    .build());

            if (snapshot.getCpuPercent() != null) {
                metrics.add(ObserviumMetricDTO.builder()
                        .hostId(hostId)
                        .hostName(hostName)
                        .itemId("cpu")
                        .metricKey("observium.snmp.cpu.percent")
                        .value(snapshot.getCpuPercent())
                        .timestamp(now)
                        .ip(snapshot.getIpAddress())
                        .port(snapshot.getSnmpPort())
                        .build());
            }

            if (snapshot.getMemoryPercent() != null) {
                metrics.add(ObserviumMetricDTO.builder()
                        .hostId(hostId)
                        .hostName(hostName)
                        .itemId("memory")
                        .metricKey("observium.snmp.memory.percent")
                        .value(snapshot.getMemoryPercent())
                        .timestamp(now)
                        .ip(snapshot.getIpAddress())
                        .port(snapshot.getSnmpPort())
                        .build());
            }

            if (snapshot.getUptimeSeconds() != null) {
                metrics.add(ObserviumMetricDTO.builder()
                        .hostId(hostId)
                        .hostName(hostName)
                        .itemId("uptime")
                        .metricKey("observium.snmp.uptime.seconds")
                        .value(snapshot.getUptimeSeconds().doubleValue())
                        .timestamp(now)
                        .ip(snapshot.getIpAddress())
                        .port(snapshot.getSnmpPort())
                        .build());
            }

            List<ObserviumInterfaceSnapshot> interfaces = snapshot.getInterfaces();
            if (interfaces != null) {
                for (ObserviumInterfaceSnapshot iface : interfaces) {
                    String prefix = "interface." + iface.getIfIndex();
                    if (iface.getInBandwidthMbps() != null) {
                        metrics.add(buildInterfaceMetric(hostId, hostName, snapshot, prefix + ".in.mbps", iface.getInBandwidthMbps(), now));
                    }
                    if (iface.getOutBandwidthMbps() != null) {
                        metrics.add(buildInterfaceMetric(hostId, hostName, snapshot, prefix + ".out.mbps", iface.getOutBandwidthMbps(), now));
                    }
                    if (iface.getUtilizationPercent() != null) {
                        metrics.add(buildInterfaceMetric(hostId, hostName, snapshot, prefix + ".utilization.percent", iface.getUtilizationPercent(), now));
                    }
                    if (iface.getInErrors() != null) {
                        metrics.add(buildInterfaceMetric(hostId, hostName, snapshot, prefix + ".in.errors", iface.getInErrors().doubleValue(), now));
                    }
                    if (iface.getOutErrors() != null) {
                        metrics.add(buildInterfaceMetric(hostId, hostName, snapshot, prefix + ".out.errors", iface.getOutErrors().doubleValue(), now));
                    }
                }
            }
        }

        return metrics;
    }

    private ObserviumMetricDTO buildInterfaceMetric(
            String hostId,
            String hostName,
            ObserviumSnmpDeviceSnapshot snapshot,
            String itemId,
            Double value,
            long now
    ) {
        return ObserviumMetricDTO.builder()
                .hostId(hostId)
                .hostName(hostName)
                .itemId(itemId)
                .metricKey("observium.snmp." + itemId)
                .value(value)
                .timestamp(now)
                .ip(snapshot.getIpAddress())
                .port(snapshot.getSnmpPort())
                .build();
    }

    private boolean isManagedObserviumCategory(String ipAddress) {
        String category = subnetClassifier.resolveCategory(ipAddress);
        return category != null && !MonitoringConstants.UNKNOWN.equalsIgnoreCase(category);
    }

}
