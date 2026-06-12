package tn.iteam.adapter.snmp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tn.iteam.adapter.snmp.SnmpInterfaceSnapshot;
import tn.iteam.dto.SnmpMetricDTO;
import tn.iteam.dto.SnmpProblemDTO;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.service.SnmpMonitoringService;
import tn.iteam.service.SnmpSourceService;
import tn.iteam.service.snmp.SnmpSubnetClassifier;
import tn.iteam.util.MonitoringConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class SnmpAdapter implements SnmpSourceService {

    private static final double UPS_BATTERY_CRITICAL_THRESHOLD = 15.0d;
    private static final double UPS_BATTERY_WARNING_THRESHOLD = 30.0d;
    private static final double UPS_LOAD_CRITICAL_THRESHOLD = 98.0d;
    private static final double UPS_LOAD_WARNING_THRESHOLD = 90.0d;
    private static final double PRINTER_TONER_CRITICAL_THRESHOLD = 10.0d;
    private static final double PRINTER_TONER_WARNING_THRESHOLD = 20.0d;
    private static final double INTERFACE_UTILIZATION_CRITICAL_THRESHOLD = 95.0d;
    private static final double INTERFACE_UTILIZATION_WARNING_THRESHOLD = 85.0d;
    private static final double CPU_CRITICAL_THRESHOLD = 95.0d;
    private static final double CPU_WARNING_THRESHOLD = 85.0d;
    private static final double MEMORY_CRITICAL_THRESHOLD = 95.0d;
    private static final double MEMORY_WARNING_THRESHOLD = 85.0d;
    private static final long INTERFACE_ERROR_CRITICAL_THRESHOLD = 100L;
    private static final long INTERFACE_ERROR_WARNING_THRESHOLD = 1L;

    private final SnmpMonitoringService snmpMonitoringService;
    private final SnmpSubnetClassifier subnetClassifier;
    private volatile List<SnmpDeviceSnapshot> lastSnapshots = List.of();
    private volatile long lastPollEpochMs = 0L;
    private static final long SNAPSHOT_CACHE_MS = 2_000L;

    private List<SnmpDeviceSnapshot> pollSnapshots() {
        long now = System.currentTimeMillis();
        if (now - lastPollEpochMs <= SNAPSHOT_CACHE_MS && !lastSnapshots.isEmpty()) {
            return lastSnapshots;
        }
        synchronized (this) {
            long refreshedNow = System.currentTimeMillis();
            if (refreshedNow - lastPollEpochMs <= SNAPSHOT_CACHE_MS && !lastSnapshots.isEmpty()) {
                return lastSnapshots;
            }
            lastSnapshots = snmpMonitoringService.pollEnabledDevices();
            lastPollEpochMs = refreshedNow;
            return lastSnapshots;
        }
    }

    @Override
    public List<ServiceStatusDTO> fetchAll() {
        List<SnmpDeviceSnapshot> snapshots = pollSnapshots();
        return snapshots.stream()
                .filter(snapshot -> subnetClassifier.isIncludedInScope(snapshot.getIpAddress()))
                .filter(this::isManagedSnmpCategory)
                .map(snapshot -> ServiceStatusDTO.builder()
                        .source(MonitoringConstants.SOURCE_SNMP)
                        .hostId(snapshot.getHostId())
                        .name(snapshot.getHostName())
                        .ip(snapshot.getIpAddress())
                        .port(snapshot.getSnmpPort())
                        .protocol("SNMP")
                        .status(snapshot.getStatus())
                        .category(resolveSnapshotCategory(snapshot))
                        .lastCheck(java.time.LocalDateTime.now())
                        .build())
                .toList();
    }

    @Override
    public List<SnmpProblemDTO> fetchProblems() {
        long now = Instant.now().getEpochSecond();
        List<SnmpDeviceSnapshot> snapshots = pollSnapshots();
        List<SnmpProblemDTO> problems = new ArrayList<>();

        for (SnmpDeviceSnapshot snapshot : snapshots) {
            if (!subnetClassifier.isIncludedInScope(snapshot.getIpAddress())) {
                continue;
            }
            if (!isManagedSnmpCategory(snapshot)) {
                continue;
            }
            if (!MonitoringConstants.STATUS_UP.equalsIgnoreCase(snapshot.getStatus())) {
                String severity = MonitoringConstants.STATUS_DOWN.equalsIgnoreCase(snapshot.getStatus()) ? "4" : "3";
                problems.add(SnmpProblemDTO.builder()
                        .problemId("OBS-SNMP-" + snapshot.getIpAddress())
                        .host(snapshot.getHostName())
                        .hostId(snapshot.getHostId())
                        .description(buildStatusProblemDescription(snapshot))
                        .severity(severity)
                        .active(true)
                        .source(MonitoringConstants.SOURCE_SNMP)
                        .eventId(now)
                        .startedAt(now)
                        .lastObservedAt(now)
                        .resolvedAt(null)
                        .build());
            }
            problems.addAll(buildDerivedProblems(snapshot, now));
        }
        return problems.stream()
                .sorted(Comparator.comparing(SnmpProblemDTO::getProblemId))
                .toList();
    }

    @Override
    public List<SnmpMetricDTO> fetchMetrics() {
        List<SnmpDeviceSnapshot> snapshots = pollSnapshots();
        long now = Instant.now().getEpochSecond();
        List<SnmpMetricDTO> metrics = new ArrayList<>();

        for (SnmpDeviceSnapshot snapshot : snapshots) {
            if (!subnetClassifier.isIncludedInScope(snapshot.getIpAddress())) {
                continue;
            }
            if (!isManagedSnmpCategory(snapshot)) {
                continue;
            }
            String hostName = snapshot.getHostName() != null && !snapshot.getHostName().isBlank() ? snapshot.getHostName() : "UNKNOWN";
            String hostId = snapshot.getHostId();

            metrics.add(SnmpMetricDTO.builder()
                    .hostId(hostId)
                    .hostName(hostName)
                    .itemId("availability")
                    .metricKey("snmp.availability")
                    .value(snapshot.getAvailability())
                    .timestamp(now)
                    .ip(snapshot.getIpAddress())
                    .port(snapshot.getSnmpPort())
                    .build());

            if (snapshot.getCpuPercent() != null) {
                metrics.add(SnmpMetricDTO.builder()
                        .hostId(hostId)
                        .hostName(hostName)
                        .itemId("cpu")
                        .metricKey("snmp.cpu.percent")
                        .value(snapshot.getCpuPercent())
                        .timestamp(now)
                        .ip(snapshot.getIpAddress())
                        .port(snapshot.getSnmpPort())
                        .build());
            }

            if (snapshot.getMemoryPercent() != null) {
                metrics.add(SnmpMetricDTO.builder()
                        .hostId(hostId)
                        .hostName(hostName)
                        .itemId("memory")
                        .metricKey("snmp.memory.percent")
                        .value(snapshot.getMemoryPercent())
                        .timestamp(now)
                        .ip(snapshot.getIpAddress())
                        .port(snapshot.getSnmpPort())
                        .build());
            }

            if (snapshot.getUptimeSeconds() != null) {
                metrics.add(SnmpMetricDTO.builder()
                        .hostId(hostId)
                        .hostName(hostName)
                        .itemId("uptime")
                        .metricKey("snmp.uptime.seconds")
                        .value(snapshot.getUptimeSeconds().doubleValue())
                        .timestamp(now)
                        .ip(snapshot.getIpAddress())
                        .port(snapshot.getSnmpPort())
                        .build());
            }

            if (snapshot.getExtraMetrics() != null) {
                snapshot.getExtraMetrics().forEach((itemId, value) -> {
                    if (value == null) {
                        return;
                    }
                    metrics.add(SnmpMetricDTO.builder()
                            .hostId(hostId)
                            .hostName(hostName)
                            .itemId(itemId)
                            .metricKey("snmp." + itemId)
                            .value(value)
                            .timestamp(now)
                            .ip(snapshot.getIpAddress())
                            .port(snapshot.getSnmpPort())
                            .build());
                });
            }

            List<SnmpInterfaceSnapshot> interfaces = snapshot.getInterfaces();
            if (interfaces != null) {
                for (SnmpInterfaceSnapshot iface : interfaces) {
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

    private SnmpMetricDTO buildInterfaceMetric(
            String hostId,
            String hostName,
            SnmpDeviceSnapshot snapshot,
            String itemId,
            Double value,
            long now
    ) {
        return SnmpMetricDTO.builder()
                .hostId(hostId)
                .hostName(hostName)
                .itemId(itemId)
                .metricKey("snmp." + itemId)
                .value(value)
                .timestamp(now)
                .ip(snapshot.getIpAddress())
                .port(snapshot.getSnmpPort())
                .build();
    }

    private boolean isManagedSnmpCategory(SnmpDeviceSnapshot snapshot) {
        String category = resolveSnapshotCategory(snapshot);
        return category != null && !MonitoringConstants.UNKNOWN.equalsIgnoreCase(category);
    }

    private List<SnmpProblemDTO> buildDerivedProblems(SnmpDeviceSnapshot snapshot, long now) {
        List<SnmpProblemDTO> problems = new ArrayList<>();
        String category = resolveSnapshotCategory(snapshot);

        if (isServerOrFirewall(category)) {
            Double cpuPercent = snapshot.getCpuPercent();
            if (cpuPercent != null && cpuPercent >= CPU_WARNING_THRESHOLD) {
                problems.add(buildDerivedProblem(
                        snapshot,
                        "cpu-high",
                        "CPU utilization is high (" + formatNumber(cpuPercent) + "%)",
                        cpuPercent >= CPU_CRITICAL_THRESHOLD ? "5" : "4",
                        now
                ));
            }

            Double memoryPercent = snapshot.getMemoryPercent();
            if (memoryPercent != null && memoryPercent >= MEMORY_WARNING_THRESHOLD) {
                problems.add(buildDerivedProblem(
                        snapshot,
                        "memory-high",
                        "Memory utilization is high (" + formatNumber(memoryPercent) + "%)",
                        memoryPercent >= MEMORY_CRITICAL_THRESHOLD ? "5" : "4",
                        now
                ));
            }
        }

        Map<String, Double> extraMetrics = snapshot.getExtraMetrics();
        if (extraMetrics != null && !extraMetrics.isEmpty()) {
            Double batteryPercent = extraMetrics.get("ups.battery.percent");
            if (batteryPercent != null && batteryPercent <= UPS_BATTERY_WARNING_THRESHOLD) {
                problems.add(buildDerivedProblem(
                        snapshot,
                        "ups-battery-low",
                        "UPS battery level is low (" + formatNumber(batteryPercent) + "%)",
                        batteryPercent <= UPS_BATTERY_CRITICAL_THRESHOLD ? "5" : "4",
                        now
                ));
            }

            Double outputLoad = extraMetrics.get("ups.output.load.percent");
            if (outputLoad != null && outputLoad >= UPS_LOAD_WARNING_THRESHOLD) {
                problems.add(buildDerivedProblem(
                        snapshot,
                        "ups-load-high",
                        "UPS output load is high (" + formatNumber(outputLoad) + "%)",
                        outputLoad >= UPS_LOAD_CRITICAL_THRESHOLD ? "5" : "4",
                        now
                ));
            }

            Double tonerPercent = extraMetrics.get("printer.toner.percent");
            if (tonerPercent != null && tonerPercent <= PRINTER_TONER_WARNING_THRESHOLD) {
                problems.add(buildDerivedProblem(
                        snapshot,
                        "printer-toner-low",
                        "Printer toner level is low (" + formatNumber(tonerPercent) + "%)",
                        tonerPercent <= PRINTER_TONER_CRITICAL_THRESHOLD ? "5" : "4",
                        now
                ));
            }
        }

        if (snapshot.getInterfaces() != null) {
            snapshot.getInterfaces().stream()
                    .filter(iface -> iface.getUtilizationPercent() != null)
                    .filter(iface -> iface.getUtilizationPercent() >= INTERFACE_UTILIZATION_WARNING_THRESHOLD)
                    .forEach(iface -> problems.add(buildDerivedProblem(
                            snapshot,
                            "interface-utilization-" + iface.getIfIndex(),
                            "Interface " + safeInterfaceName(iface.getName(), iface.getIfIndex())
                                    + " utilization is high (" + formatNumber(iface.getUtilizationPercent()) + "%)",
                            iface.getUtilizationPercent() >= INTERFACE_UTILIZATION_CRITICAL_THRESHOLD ? "5" : "4",
                            now
                    )));
            snapshot.getInterfaces().stream()
                    .filter(iface -> {
                        long recentErrors = coalesce(iface.getRecentInErrors()) + coalesce(iface.getRecentOutErrors());
                        return recentErrors >= INTERFACE_ERROR_WARNING_THRESHOLD;
                    })
                    .forEach(iface -> {
                        long recentErrors = coalesce(iface.getRecentInErrors()) + coalesce(iface.getRecentOutErrors());
                        problems.add(buildDerivedProblem(
                                snapshot,
                                "interface-errors-" + iface.getIfIndex(),
                                "Interface " + safeInterfaceName(iface.getName(), iface.getIfIndex())
                                        + " reported recent errors (" + recentErrors + ")",
                                recentErrors >= INTERFACE_ERROR_CRITICAL_THRESHOLD ? "5" : "4",
                                now
                        ));
                    });
        }

        return problems;
    }

    private SnmpProblemDTO buildDerivedProblem(
            SnmpDeviceSnapshot snapshot,
            String suffix,
            String description,
            String severity,
            long now
    ) {
        String hostName = snapshot.getHostName() != null && !snapshot.getHostName().isBlank()
                ? snapshot.getHostName()
                : snapshot.getIpAddress();
        return SnmpProblemDTO.builder()
                .problemId("OBS-SNMP-" + snapshot.getIpAddress() + "-" + suffix)
                .host(hostName)
                .hostId(snapshot.getHostId())
                .description(description)
                .severity(severity)
                .active(true)
                .source(MonitoringConstants.SOURCE_SNMP)
                .eventId(now)
                .startedAt(now)
                .lastObservedAt(now)
                .resolvedAt(null)
                .build();
    }

    private String safeInterfaceName(String name, Integer ifIndex) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return "ifIndex-" + ifIndex;
    }

    private boolean isServerOrFirewall(String category) {
        return MonitoringConstants.CATEGORY_SERVER.equals(category)
                || MonitoringConstants.CATEGORY_FIREWALL.equals(category);
    }

    private long coalesce(Long value) {
        return value != null ? value : 0L;
    }

    private String formatNumber(Double value) {
        if (value == null) {
            return "0";
        }
        if (Math.abs(value - Math.rint(value)) < 0.0001d) {
            return Long.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private String resolveSnapshotCategory(SnmpDeviceSnapshot snapshot) {
        if (snapshot.getCategory() != null && !snapshot.getCategory().isBlank()) {
            return snapshot.getCategory();
        }
        return subnetClassifier.resolveCategory(snapshot.getIpAddress());
    }

    private String buildStatusProblemDescription(SnmpDeviceSnapshot snapshot) {
        String status = snapshot.getStatus();
        String sysDescr = snapshot.getSysDescr();
        if (sysDescr == null || sysDescr.isBlank() || MonitoringConstants.UNKNOWN.equalsIgnoreCase(sysDescr)) {
            return "SNMP device status is " + status;
        }
        if (MonitoringConstants.STATUS_DOWN.equalsIgnoreCase(status)) {
            return "SNMP device is DOWN: " + sysDescr;
        }
        if (MonitoringConstants.STATUS_DEGRADED.equalsIgnoreCase(status)) {
            return "SNMP device is DEGRADED: partial response (" + sysDescr + ")";
        }
        return "SNMP device status is " + status + " (" + sysDescr + ")";
    }

}
