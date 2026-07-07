package tn.iteam.service.snmp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import tn.iteam.config.SnmpProperties;
import tn.iteam.domain.SnmpDevice;
import tn.iteam.domain.SnmpProblem;
import tn.iteam.domain.ServiceStatus;
import tn.iteam.domain.MonitoredHost;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.enums.SnmpDeviceType;
import tn.iteam.repository.MonitoredHostRepository;
import tn.iteam.repository.SnmpDeviceRepository;
import tn.iteam.repository.SnmpProblemRepository;
import tn.iteam.repository.ServiceStatusRepository;
import tn.iteam.util.MonitoringConstants;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SnmpDeviceBootstrap implements ApplicationRunner {

    private final SnmpDeviceRepository deviceRepository;
    private final MonitoredHostRepository monitoredHostRepository;
    private final ServiceStatusRepository serviceStatusRepository;
    private final SnmpProblemRepository snmpProblemRepository;
    private final SnmpProperties properties;
    private final SnmpSubnetClassifier subnetClassifier;



    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("SNMP device bootstrap skipped: SNMP is disabled.");
            return;
        }

        try {
            Set<String> seedAddresses = resolveSeedAddresses();
            Set<String> trackedAddresses = resolveTrackedAddresses(seedAddresses);

            if (trackedAddresses.isEmpty()) {
                log.info("SNMP device bootstrap skipped: no seed addresses configured.");
                return;
            }

            int removed = pruneObsoleteDevices(trackedAddresses);
            int removedHosts = pruneObsoleteMonitoredHosts(trackedAddresses);
            int removedStatuses = pruneObsoleteServiceStatuses(trackedAddresses);
            int resolvedProblems = resolveObsoleteProblems(trackedAddresses);

            int created = 0;

            for (String address : seedAddresses) {
                var existing = deviceRepository.findByIpAddress(address);

                if (existing.isPresent()) {
                    SnmpDevice device = existing.get();

                    if ((device.getCategory() == null || device.getCategory().isBlank())
                            && !Boolean.TRUE.equals(device.getManualEntry())) {
                        device.setCategory(subnetClassifier.resolveConfiguredCategory(address));
                    }

                    if (device.getManualEntry() == null) {
                        device.setManualEntry(false);
                    }

                    if (device.getType() == null) {
                        device.setType(SnmpDeviceType.OTHER);
                    }

                    if (device.getPollingIntervalSeconds() == null || device.getPollingIntervalSeconds() <= 0) {
                        device.setPollingIntervalSeconds(properties.getDefaultPollingIntervalSeconds());
                    }

                    if (device.getMetricsToPoll() == null || device.getMetricsToPoll().isEmpty()) {
                        device.setMetricsToPoll(new LinkedHashSet<>(properties.getDefaultMetricsToPoll()));
                    }

                    deviceRepository.save(device);
                    continue;
                }

                SnmpDevice device = new SnmpDevice();
                device.setIpAddress(address);
                device.setHostname(address);
                device.setCategory(subnetClassifier.resolveConfiguredCategory(address));
                device.setSnmpPort(properties.getDefaultPort());
                device.setSnmpCommunity(properties.getDefaultCommunity());
                device.setSnmpVersion(properties.getDefaultVersion());
                device.setStatus(DeviceStatus.UNKNOWN);
                device.setEnabled(true);
                device.setManualEntry(false);
                device.setType(SnmpDeviceType.OTHER);
                device.setPollingIntervalSeconds(properties.getDefaultPollingIntervalSeconds());
                device.setMetricsToPoll(new LinkedHashSet<>(properties.getDefaultMetricsToPoll()));

                deviceRepository.save(device);
                created++;
            }

            log.info(
                    "SNMP bootstrap completed: created={}, removedDevices={}, removedHosts={}, removedStatuses={}, resolvedProblems={}",
                    created,
                    removed,
                    removedHosts,
                    removedStatuses,
                    resolvedProblems
            );

        } catch (DataAccessException ex) {
            log.warn("SNMP bootstrap skipped because database is unavailable: {}", ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            log.warn("SNMP bootstrap skipped because Spring context is not ready or is shutting down: {}", ex.getMessage(), ex);
        } catch (Exception ex) {
            log.warn("SNMP bootstrap skipped due to unexpected error: {}", ex.getMessage(), ex);
        }
    }

    private int pruneObsoleteDevices(Set<String> seedAddresses) {
        int removed = 0;
        for (SnmpDevice device : deviceRepository.findAllByOrderByIpAddressAsc()) {
            String ipAddress = device.getIpAddress();
            if (Boolean.TRUE.equals(device.getManualEntry()) || ipAddress == null || seedAddresses.contains(ipAddress)) {
                continue;
            }
            deviceRepository.delete(device);
            removed++;
        }
        return removed;
    }

    private int pruneObsoleteMonitoredHosts(Set<String> seedAddresses) {
        List<MonitoredHost> obsolete = monitoredHostRepository.findBySource(MonitoringConstants.SOURCE_SNMP).stream()
                .filter(host -> host.getIp() == null || !seedAddresses.contains(host.getIp()))
                .toList();
        if (obsolete.isEmpty()) {
            return 0;
        }
        monitoredHostRepository.deleteAll(obsolete);
        return obsolete.size();
    }

    private int pruneObsoleteServiceStatuses(Set<String> seedAddresses) {
        List<ServiceStatus> obsolete = serviceStatusRepository.findBySource(MonitoringConstants.SOURCE_SNMP).stream()
                .filter(status -> status.getIp() == null || !seedAddresses.contains(status.getIp()))
                .toList();
        if (obsolete.isEmpty()) {
            return 0;
        }
        serviceStatusRepository.deleteAll(obsolete);
        return obsolete.size();
    }

    private int resolveObsoleteProblems(Set<String> seedAddresses) {
        long now = Instant.now().getEpochSecond();
        List<SnmpProblem> obsolete = snmpProblemRepository.findBySourceAndActiveTrue(MonitoringConstants.SOURCE_SNMP).stream()
                .filter(problem -> problem.getHostId() == null || !seedAddresses.contains(problem.getHostId()))
                .toList();
        if (obsolete.isEmpty()) {
            return 0;
        }
        obsolete.forEach(problem -> {
            problem.setActive(false);
            if (problem.getResolvedAt() == null || problem.getResolvedAt() == 0L) {
                problem.setResolvedAt(now);
            }
        });
        snmpProblemRepository.saveAll(obsolete);
        return obsolete.size();
    }

    private Set<String> resolveSeedAddresses() {
        Set<String> addresses = new LinkedHashSet<>();

        List<String> direct = properties.getSeedAddresses();
        if (direct != null) {
            for (String raw : direct) {
                if (raw != null && !raw.isBlank()) {
                    addresses.add(raw.trim());
                }
            }
        }

        List<String> ranges = properties.getSeedRanges();
        if (ranges != null) {
            for (String rawRange : ranges) {
                addresses.addAll(expandRange(rawRange));
            }
        }
        return addresses;
    }

    private Set<String> resolveTrackedAddresses(Set<String> seedAddresses) {
        Set<String> tracked = new LinkedHashSet<>(seedAddresses);
        deviceRepository.findAllByOrderByIpAddressAsc().stream()
                .filter(device -> Boolean.TRUE.equals(device.getManualEntry()))
                .map(SnmpDevice::getIpAddress)
                .filter(ip -> ip != null && !ip.isBlank())
                .forEach(tracked::add);
        return tracked;
    }

    private Set<String> expandRange(String rawRange) {
        Set<String> expanded = new LinkedHashSet<>();
        if (rawRange == null || rawRange.isBlank()) {
            return expanded;
        }

        String range = rawRange.trim();
        if (range.contains("/")) {
            expanded.addAll(expandCidr24(range));
            return expanded;
        }
        if (range.contains("-")) {
            expanded.addAll(expandDashRange(range));
            return expanded;
        }

        expanded.add(range);
        return expanded;
    }

    private Set<String> expandCidr24(String cidr) {
        Set<String> result = new LinkedHashSet<>();
        String[] parts = cidr.split("/");
        if (parts.length != 2 || !"24".equals(parts[1].trim())) {
            return result;
        }
        String[] octets = parts[0].trim().split("\\.");
        if (octets.length != 4) {
            return result;
        }
        String prefix = octets[0] + "." + octets[1] + "." + octets[2] + ".";
        for (int i = 1; i <= 254; i++) {
            result.add(prefix + i);
        }
        return result;
    }

    private Set<String> expandDashRange(String rawRange) {
        Set<String> result = new LinkedHashSet<>();
        String[] parts = rawRange.split("-");
        if (parts.length != 2) {
            return result;
        }

        String left = parts[0].trim();
        String right = parts[1].trim();
        String[] leftOctets = left.split("\\.");

        try {
            if (leftOctets.length == 4 && !right.contains(".")) {
                int start = Integer.parseInt(leftOctets[3]);
                int end = Integer.parseInt(right);
                String prefix = leftOctets[0] + "." + leftOctets[1] + "." + leftOctets[2] + ".";
                appendHostRange(result, prefix, start, end);
                return result;
            }

            String[] rightOctets = right.split("\\.");
            if (leftOctets.length == 4 && rightOctets.length == 4
                    && leftOctets[0].equals(rightOctets[0])
                    && leftOctets[1].equals(rightOctets[1])
                    && leftOctets[2].equals(rightOctets[2])) {
                int start = Integer.parseInt(leftOctets[3]);
                int end = Integer.parseInt(rightOctets[3]);
                String prefix = leftOctets[0] + "." + leftOctets[1] + "." + leftOctets[2] + ".";
                appendHostRange(result, prefix, start, end);
            }
        } catch (NumberFormatException ignored) {
            // Ignore malformed range fragments and keep bootstrap resilient.
        }

        return result;
    }

    private void appendHostRange(Set<String> result, String prefix, int start, int end) {
        int lower = Math.max(1, Math.min(start, end));
        int upper = Math.min(254, Math.max(start, end));
        for (int i = lower; i <= upper; i++) {
            result.add(prefix + i);
        }
    }
}
