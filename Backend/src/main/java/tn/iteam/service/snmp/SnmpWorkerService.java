package tn.iteam.service.snmp;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TreeEvent;
import org.snmp4j.util.TreeUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.snmp.SnmpDeviceSnapshot;
import tn.iteam.adapter.snmp.SnmpInterfaceSnapshot;
import tn.iteam.config.SnmpProperties;
import tn.iteam.domain.SnmpDevice;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.service.SnmpCategoryMetricsService;
import tn.iteam.service.SnmpInterfaceCollectionService;
import tn.iteam.util.MonitoringConstants;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnmpWorkerService {

    private static final String RESILIENCE_NAME = "snmpApi";
    private static final OID OID_SYS_DESCR = new OID("1.3.6.1.2.1.1.1.0");
    private static final OID OID_SYS_UPTIME = new OID("1.3.6.1.2.1.1.3.0");
    private static final OID OID_SYS_NAME = new OID("1.3.6.1.2.1.1.5.0");
    private static final OID OID_HR_PROCESSOR_LOAD = new OID("1.3.6.1.2.1.25.3.3.1.2");
    private static final OID OID_HR_MEMORY_SIZE_KB = new OID("1.3.6.1.2.1.25.2.2.0");
    private static final OID OID_UCD_MEM_AVAIL_REAL_KB = new OID("1.3.6.1.4.1.2021.4.6.0");

    private final SnmpProperties properties;
    private final SnmpSubnetClassifier subnetClassifier;
    private final SnmpInterfaceCollectionService interfaceCollectionService;
    private final SnmpCategoryMetricsService categoryMetricsService;
    private final @Qualifier("snmpTaskExecutor") ThreadPoolTaskExecutor snmpTaskExecutor;

    private record OidProbeResult(Optional<Variable> value, String failureReason) {
        boolean responded() {
            return value != null && value.isPresent();
        }
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "pollDeviceFallback")
    @TimeLimiter(name = RESILIENCE_NAME, fallbackMethod = "pollDeviceFallback")
    public CompletableFuture<SnmpDeviceSnapshot> pollDeviceAsync(SnmpDevice device) {
        return CompletableFuture.supplyAsync(() -> probeDevice(device), snmpTaskExecutor);
    }

    private CompletableFuture<SnmpDeviceSnapshot> pollDeviceFallback(SnmpDevice device, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        long now = Instant.now().getEpochSecond();
        String ip = device != null ? device.getIpAddress() : "unknown";
        Integer port = device != null && device.getSnmpPort() != null ? device.getSnmpPort() : properties.getDefaultPort();
        String reason = SnmpExceptionUtils.safeMessage(cause);
        if (cause instanceof CallNotPermittedException) {
            log.warn("SNMP device {}:{} skipped because resilience rejected the call. Preserving previous state: {}", ip, port, reason);
            return CompletableFuture.completedFuture(buildUnavailableSnapshot(device, now, reason));
        }
        log.warn("SNMP device {}:{} marked DOWN by resilience fallback: {}", ip, port, reason);
        return CompletableFuture.completedFuture(buildDownSnapshot(device, now, reason));
    }

    private SnmpDeviceSnapshot probeDevice(SnmpDevice device) {
        long now = Instant.now().getEpochSecond();
        String ip = device != null ? device.getIpAddress() : null;
        Integer port = device != null && device.getSnmpPort() != null ? device.getSnmpPort() : properties.getDefaultPort();
        String community = device != null && device.getSnmpCommunity() != null && !device.getSnmpCommunity().isBlank()
                ? device.getSnmpCommunity()
                : properties.getDefaultCommunity();
        SnmpExceptionUtils.validateDeviceConfiguration(device, ip, port, community);

        try (TransportMapping<?> transport = new DefaultUdpTransportMapping();
             Snmp snmp = new Snmp(transport)) {
            transport.listen();
            Target<Address> target = buildTarget(device, ip, port, community);
            List<String> failures = new ArrayList<>();
            List<String> successfulOids = new ArrayList<>();
            Set<String> metricsToPoll = normalizeMetrics(device.getMetricsToPoll());

            OidProbeResult sysNameProbe = snmpGetSafe(snmp, target, OID_SYS_NAME, "sysName");
            OidProbeResult sysDescrProbe = snmpGetSafe(snmp, target, OID_SYS_DESCR, "sysDescr");
            OidProbeResult sysUptimeProbe = shouldCollect(metricsToPoll, "SYSTEM")
                    ? snmpGetSafe(snmp, target, OID_SYS_UPTIME, "sysUpTime")
                    : new OidProbeResult(Optional.empty(), null);
            OidProbeResult totalMemProbe = shouldCollect(metricsToPoll, "SYSTEM")
                    ? snmpGetSafe(snmp, target, OID_HR_MEMORY_SIZE_KB, "hrMemorySize")
                    : new OidProbeResult(Optional.empty(), null);
            OidProbeResult availMemProbe = shouldCollect(metricsToPoll, "SYSTEM")
                    ? snmpGetSafe(snmp, target, OID_UCD_MEM_AVAIL_REAL_KB, "ucdMemAvailReal")
                    : new OidProbeResult(Optional.empty(), null);

            collectProbeOutcome("sysName", sysNameProbe, successfulOids, failures);
            collectProbeOutcome("sysDescr", sysDescrProbe, successfulOids, failures);
            collectProbeOutcome("sysUpTime", sysUptimeProbe, successfulOids, failures);
            collectProbeOutcome("hrMemorySize", totalMemProbe, successfulOids, failures);
            collectProbeOutcome("ucdMemAvailReal", availMemProbe, successfulOids, failures);

            Optional<String> sysNameResponse = sysNameProbe.value().map(Variable::toString);
            Optional<String> sysDescrResponse = sysDescrProbe.value().map(Variable::toString);
            String sysName = sysNameResponse.orElse(ip);
            String sysDescr = sysDescrResponse.orElse(MonitoringConstants.UNKNOWN);
            Long uptimeSeconds = sysUptimeProbe.value().map(this::parseLong).map(ticks -> ticks / 100).orElse(null);

            OptionalDouble cpuAvg = shouldCollect(metricsToPoll, "SYSTEM")
                    ? snmpWalkAverage(snmp, target, OID_HR_PROCESSOR_LOAD)
                    : OptionalDouble.empty();
            Long totalMemKb = totalMemProbe.value().map(this::parseLong).orElse(null);
            Long availMemKb = availMemProbe.value().map(this::parseLong).orElse(null);
            Double memUsage = shouldCollect(metricsToPoll, "SYSTEM") ? computeMemoryUsagePercent(totalMemKb, availMemKb) : null;
            String category = resolveDeviceCategory(device);
            Map<String, Double> extraMetrics = shouldCollect(metricsToPoll, "CATEGORY")
                    ? safeCollectCategoryMetrics(snmp, target, category, failures, successfulOids)
                    : Map.of();
            List<SnmpInterfaceSnapshot> interfaces = shouldCollect(metricsToPoll, "INTERFACES")
                    ? safeCollectInterfaces(snmp, target, ip, now, failures, successfulOids)
                    : List.of();

            boolean hasIdentityResponse = sysNameResponse.isPresent() || sysDescrResponse.isPresent() || uptimeSeconds != null;
            boolean hasOperationalMetrics = cpuAvg.isPresent() || memUsage != null || !interfaces.isEmpty() || !extraMetrics.isEmpty();
            if (!hasIdentityResponse && !hasOperationalMetrics) {
                String reason = failures.isEmpty() ? "No SNMP response" : String.join("; ", failures);
                return buildDownSnapshot(device, now, reason, successfulOids, failures);
            }

            boolean partialResponse = !hasIdentityResponse
                    || (shouldCollect(metricsToPoll, "SYSTEM") && (cpuAvg.isEmpty() || memUsage == null))
                    || (shouldCollect(metricsToPoll, "CATEGORY") && extraMetrics.isEmpty())
                    || (shouldCollect(metricsToPoll, "INTERFACES") && interfaces.isEmpty());
            DeviceStatus status = partialResponse ? DeviceStatus.DEGRADED : DeviceStatus.UP;

            return SnmpDeviceSnapshot.builder()
                    .ipAddress(ip)
                    .hostId(ip)
                    .hostName(sysName)
                    .category(category)
                    .snmpPort(port)
                    .status(status.name())
                    .deviceStatus(status)
                    .availability(status == DeviceStatus.UP ? 1.0 : 0.5)
                    .cpuPercent(cpuAvg.isPresent() ? cpuAvg.getAsDouble() : null)
                    .memoryPercent(memUsage)
                    .uptimeSeconds(uptimeSeconds)
                    .sysDescr(sysDescr)
                    .interfaces(interfaces)
                    .extraMetrics(extraMetrics)
                    .diagnosticReason(buildDiagnosticReason(status, successfulOids, failures, interfaces))
                    .successfulOids(List.copyOf(successfulOids))
                    .failedOids(List.copyOf(failures))
                    .pollAttempted(true)
                    .collectedAtEpochSec(now)
                    .build();
        } catch (IOException exception) {
            throw SnmpExceptionUtils.classifyIoException(device, exception);
        }
    }

    private Set<String> normalizeMetrics(Set<String> metricsToPoll) {
        if (metricsToPoll == null || metricsToPoll.isEmpty()) {
            return new LinkedHashSet<>(properties.getDefaultMetricsToPoll());
        }
        return metricsToPoll.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean shouldCollect(Set<String> metricsToPoll, String metricFamily) {
        return metricsToPoll == null || metricsToPoll.isEmpty() || metricsToPoll.contains(metricFamily);
    }

    private String resolveDeviceCategory(SnmpDevice device) {
        if (device == null) {
            return MonitoringConstants.UNKNOWN;
        }
        if (device.getCategory() != null && !device.getCategory().isBlank()) {
            return device.getCategory().trim();
        }
        return subnetClassifier.resolveConfiguredCategory(device.getIpAddress());
    }

    private Target<Address> buildTarget(SnmpDevice device, String ip, int port, String community) {
        CommunityTarget<Address> target = new CommunityTarget<>();
        target.setCommunity(new OctetString(community));
        target.setAddress(GenericAddress.parse("udp:" + ip + "/" + port));
        target.setVersion(resolveSnmpVersion(device));
        target.setTimeout(properties.getTimeoutMs());
        target.setRetries(Math.max(0, properties.getRetries()));
        return target;
    }

    private int resolveSnmpVersion(SnmpDevice device) {
        String version = device != null && device.getSnmpVersion() != null && !device.getSnmpVersion().isBlank()
                ? device.getSnmpVersion().trim()
                : properties.getDefaultVersion();
        return switch (version.toLowerCase(Locale.ROOT)) {
            case "1" -> SnmpConstants.version1;
            case "3" -> SnmpConstants.version3;
            default -> SnmpConstants.version2c;
        };
    }

    private OptionalDouble snmpWalkAverage(Snmp snmp, Target<Address> target, OID baseOid) {
        List<Variable> values = walkValues(snmp, target, baseOid);
        if (values.isEmpty()) {
            return OptionalDouble.empty();
        }
        double sum = 0;
        int count = 0;
        for (Variable value : values) {
            try {
                sum += Double.parseDouble(value.toString());
                count++;
            } catch (NumberFormatException ignored) {
            }
        }
        return count == 0 ? OptionalDouble.empty() : OptionalDouble.of(sum / count);
    }

    private List<Variable> walkValues(Snmp snmp, Target<Address> target, OID baseOid) {
        try {
            TreeUtils treeUtils = new TreeUtils(snmp, new DefaultPDUFactory(PDU.GETNEXT));
            List<TreeEvent> events = treeUtils.getSubtree(target, baseOid);
            if (events == null || events.isEmpty()) {
                return List.of();
            }
            List<Variable> values = new ArrayList<>();
            for (TreeEvent event : events) {
                if (event == null || event.isError()) {
                    continue;
                }
                VariableBinding[] bindings = event.getVariableBindings();
                if (bindings == null) {
                    continue;
                }
                for (VariableBinding binding : bindings) {
                    if (binding == null || binding.getVariable() == null || binding.isException()) {
                        continue;
                    }
                    values.add(binding.getVariable());
                }
            }
            return values;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Optional<Variable> snmpGet(Snmp snmp, Target<Address> target, OID oid) throws IOException {
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(oid));
        pdu.setType(PDU.GET);
        ResponseEvent<Address> event = snmp.get(pdu, target);
        if (event == null || event.getResponse() == null || event.getResponse().size() == 0) {
            throw new IntegrationUnavailableException(MonitoringConstants.SOURCE_SNMP, "No SNMP response from " + target.getAddress());
        }
        VariableBinding binding = event.getResponse().get(0);
        if (binding == null || binding.getVariable() == null || binding.isException()) {
            throw new IntegrationResponseException(MonitoringConstants.SOURCE_SNMP, "Invalid SNMP response from " + target.getAddress());
        }
        return Optional.of(binding.getVariable());
    }

    private OidProbeResult snmpGetSafe(Snmp snmp, Target<Address> target, OID oid, String oidLabel) {
        try {
            return new OidProbeResult(snmpGet(snmp, target, oid), null);
        } catch (Exception exception) {
            log.debug("SNMP OID failed oid={} reason={}", oidLabel, SnmpExceptionUtils.safeMessage(exception));
            return new OidProbeResult(Optional.empty(), SnmpExceptionUtils.safeMessage(exception));
        }
    }

    private Map<String, Double> safeCollectCategoryMetrics(
            Snmp snmp,
            Target<Address> target,
            String category,
            List<String> failures,
            List<String> successfulOids
    ) {
        try {
            Map<String, Double> metrics = categoryMetricsService.collectCategoryMetrics(snmp, target, category);
            if (!metrics.isEmpty()) {
                successfulOids.add("categoryMetrics");
            }
            return metrics;
        } catch (Exception exception) {
            failures.add("categoryMetrics=" + SnmpExceptionUtils.safeMessage(exception));
            return Map.of();
        }
    }

    private List<SnmpInterfaceSnapshot> safeCollectInterfaces(
            Snmp snmp,
            Target<Address> target,
            String ip,
            long now,
            List<String> failures,
            List<String> successfulOids
    ) {
        try {
            List<SnmpInterfaceSnapshot> interfaces = interfaceCollectionService.collectInterfaces(snmp, target, ip, now);
            if (!interfaces.isEmpty()) {
                successfulOids.add("interfaces");
            }
            return interfaces;
        } catch (Exception exception) {
            failures.add("interfaces=" + SnmpExceptionUtils.safeMessage(exception));
            return List.of();
        }
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private Long parseLong(Variable variable) {
        if (variable instanceof Integer32 integer32) {
            return (long) integer32.getValue();
        }
        try {
            return Long.parseLong(variable.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Double computeMemoryUsagePercent(Long totalKb, Long availableKb) {
        if (totalKb == null || availableKb == null || totalKb <= 0) {
            return null;
        }
        double used = Math.max(0, totalKb - availableKb);
        return (used / totalKb) * 100.0;
    }

    private SnmpDeviceSnapshot buildDownSnapshot(SnmpDevice device, long now, String reason) {
        return buildDownSnapshot(device, now, reason, List.of(), List.of(reason));
    }

    private SnmpDeviceSnapshot buildDownSnapshot(
            SnmpDevice device,
            long now,
            String reason,
            List<String> successfulOids,
            List<String> failedOids
    ) {
        String ip = device != null ? device.getIpAddress() : "unknown";
        Integer port = device != null && device.getSnmpPort() != null ? device.getSnmpPort() : properties.getDefaultPort();
        return SnmpDeviceSnapshot.builder()
                .ipAddress(ip)
                .hostId(ip)
                .hostName(device != null && device.getHostname() != null ? device.getHostname() : ip)
                .category(device != null ? resolveDeviceCategory(device) : MonitoringConstants.UNKNOWN)
                .snmpPort(port)
                .status(MonitoringConstants.STATUS_DOWN)
                .deviceStatus(DeviceStatus.DOWN)
                .availability(0.0)
                .cpuPercent(null)
                .memoryPercent(null)
                .uptimeSeconds(null)
                .sysDescr(reason != null && !reason.isBlank() ? reason : MonitoringConstants.UNKNOWN)
                .interfaces(List.of())
                .extraMetrics(Map.of())
                .diagnosticReason(reason != null && !reason.isBlank() ? reason : "No SNMP response received after retries")
                .successfulOids(successfulOids != null ? List.copyOf(successfulOids) : List.of())
                .failedOids(failedOids != null ? List.copyOf(failedOids) : List.of())
                .pollAttempted(true)
                .collectedAtEpochSec(now)
                .build();
    }

    private SnmpDeviceSnapshot buildUnavailableSnapshot(SnmpDevice device, long now, String reason) {
        DeviceStatus preservedStatus = resolvePreservedStatus(device);
        String diagnosticReason = reason != null && !reason.isBlank()
                ? "SNMP poll skipped because resilience rejected the call: " + reason
                : "SNMP poll skipped because resilience rejected the call";
        return SnmpDeviceSnapshot.builder()
                .ipAddress(device != null ? device.getIpAddress() : "unknown")
                .hostId(device != null ? device.getIpAddress() : "unknown")
                .hostName(device != null && device.getHostname() != null ? device.getHostname() : (device != null ? device.getIpAddress() : "unknown"))
                .category(device != null ? resolveDeviceCategory(device) : MonitoringConstants.UNKNOWN)
                .snmpPort(device != null && device.getSnmpPort() != null ? device.getSnmpPort() : properties.getDefaultPort())
                .status(preservedStatus.name())
                .deviceStatus(preservedStatus)
                .availability(resolveAvailability(preservedStatus))
                .cpuPercent(null)
                .memoryPercent(null)
                .uptimeSeconds(null)
                .sysDescr("SNMP_UNAVAILABLE")
                .interfaces(List.of())
                .extraMetrics(Map.of())
                .diagnosticReason(diagnosticReason)
                .successfulOids(List.of())
                .failedOids(reason != null && !reason.isBlank() ? List.of(reason) : List.of())
                .pollAttempted(false)
                .collectedAtEpochSec(now)
                .build();
    }

    private DeviceStatus resolvePreservedStatus(SnmpDevice device) {
        if (device == null || device.getStatus() == null || device.getStatus() == DeviceStatus.DISABLED) {
            return DeviceStatus.UNKNOWN;
        }
        return device.getStatus();
    }

    private Double resolveAvailability(DeviceStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case UP -> 1.0;
            case DEGRADED -> 0.5;
            case DOWN -> 0.0;
            default -> null;
        };
    }

    private void collectProbeOutcome(String oidLabel, OidProbeResult probe, List<String> successfulOids, List<String> failures) {
        if (probe == null) {
            return;
        }
        if (probe.responded()) {
            successfulOids.add(oidLabel);
            return;
        }
        if (probe.failureReason() != null && !probe.failureReason().isBlank()) {
            failures.add(oidLabel + "=" + probe.failureReason());
        }
    }

    private String buildDiagnosticReason(
            DeviceStatus status,
            List<String> successfulOids,
            List<String> failures,
            List<SnmpInterfaceSnapshot> interfaces
    ) {
        String successText = successfulOids == null || successfulOids.isEmpty() ? "none" : String.join(", ", successfulOids);
        String failureText = failures == null || failures.isEmpty() ? "none" : String.join("; ", failures);
        int interfaceCount = interfaces != null ? interfaces.size() : 0;
        if (status == DeviceStatus.UP) {
            return "SNMP identity response received: " + successText + " | interfaces=" + interfaceCount;
        }
        if (status == DeviceStatus.DEGRADED) {
            return "Partial SNMP response: successful=" + successText + " | failed=" + failureText + " | interfaces=" + interfaceCount;
        }
        return "No SNMP response received after retries";
    }
}
