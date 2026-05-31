package tn.iteam.service.observium;

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
import tn.iteam.adapter.observium.ObserviumInterfaceSnapshot;
import tn.iteam.adapter.observium.ObserviumSnmpDeviceSnapshot;
import tn.iteam.config.ObserviumSnmpProperties;
import tn.iteam.domain.ObserviumDevice;
import tn.iteam.domain.ObserviumInterface;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.repository.ObserviumDeviceRepository;
import tn.iteam.repository.ObserviumInterfaceRepository;
import tn.iteam.util.MonitoringConstants;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ObserviumSnmpPollingService {

    private static final OID OID_SYS_DESCR = new OID("1.3.6.1.2.1.1.1.0");
    private static final OID OID_SYS_UPTIME = new OID("1.3.6.1.2.1.1.3.0");
    private static final OID OID_SYS_NAME = new OID("1.3.6.1.2.1.1.5.0");
    private static final OID OID_HR_PROCESSOR_LOAD = new OID("1.3.6.1.2.1.25.3.3.1.2");
    private static final OID OID_HR_MEMORY_SIZE_KB = new OID("1.3.6.1.2.1.25.2.2.0");
    private static final OID OID_UCD_MEM_AVAIL_REAL_KB = new OID("1.3.6.1.4.1.2021.4.6.0");

    private static final OID OID_IF_DESCR = new OID("1.3.6.1.2.1.2.2.1.2");
    private static final OID OID_IF_ADMIN_STATUS = new OID("1.3.6.1.2.1.2.2.1.7");
    private static final OID OID_IF_OPER_STATUS = new OID("1.3.6.1.2.1.2.2.1.8");
    private static final OID OID_IF_IN_OCTETS = new OID("1.3.6.1.2.1.2.2.1.10");
    private static final OID OID_IF_IN_ERRORS = new OID("1.3.6.1.2.1.2.2.1.14");
    private static final OID OID_IF_OUT_OCTETS = new OID("1.3.6.1.2.1.2.2.1.16");
    private static final OID OID_IF_OUT_ERRORS = new OID("1.3.6.1.2.1.2.2.1.20");
    private static final OID OID_IF_SPEED = new OID("1.3.6.1.2.1.2.2.1.5");

    private final ObserviumDeviceRepository deviceRepository;
    private final ObserviumInterfaceRepository interfaceRepository;
    private final ObserviumSnmpProperties properties;
    private final @Qualifier("observiumSnmpTaskExecutor") ThreadPoolTaskExecutor observiumSnmpTaskExecutor;

    public List<ObserviumSnmpDeviceSnapshot> pollEnabledDevices() {
        List<ObserviumDevice> devices = deviceRepository.findByEnabledTrueOrderByIpAddressAsc();
        if (devices.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<ObserviumSnmpDeviceSnapshot>> futures = devices.stream()
                .map(device -> CompletableFuture.supplyAsync(
                        () -> pollSingleDevice(device),
                        observiumSnmpTaskExecutor
                ))
                .toList();

        List<ObserviumSnmpDeviceSnapshot> snapshots = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        persistObservedStatus(devices, snapshots);
        persistInterfaces(snapshots);
        logCycleSummary(snapshots);
        return snapshots;
    }

    private ObserviumSnmpDeviceSnapshot pollSingleDevice(ObserviumDevice device) {
        long now = Instant.now().getEpochSecond();
        String ip = device.getIpAddress();
        Integer port = device.getSnmpPort() != null ? device.getSnmpPort() : properties.getDefaultPort();
        String community = device.getSnmpCommunity() != null && !device.getSnmpCommunity().isBlank()
                ? device.getSnmpCommunity()
                : properties.getDefaultCommunity();

        try (TransportMapping<?> transport = new DefaultUdpTransportMapping();
             Snmp snmp = new Snmp(transport)) {

            transport.listen();
            Target<Address> target = buildTarget(ip, port, community);

            String sysName = snmpGetAsString(snmp, target, OID_SYS_NAME).orElse(ip);
            String sysDescr = snmpGetAsString(snmp, target, OID_SYS_DESCR).orElse(MonitoringConstants.UNKNOWN);
            Long uptimeSeconds = snmpGetAsLong(snmp, target, OID_SYS_UPTIME).map(ticks -> ticks / 100).orElse(null);
            OptionalDouble cpuAvg = snmpWalkAverage(snmp, target, OID_HR_PROCESSOR_LOAD);
            Long totalMemKb = snmpGetAsLong(snmp, target, OID_HR_MEMORY_SIZE_KB).orElse(null);
            Long availMemKb = snmpGetAsLong(snmp, target, OID_UCD_MEM_AVAIL_REAL_KB).orElse(null);
            Double memUsage = computeMemoryUsagePercent(totalMemKb, availMemKb);

            List<ObserviumInterfaceSnapshot> interfaces = collectInterfaces(snmp, target, ip, now);
            boolean hasCoreMetrics = uptimeSeconds != null || cpuAvg.isPresent() || memUsage != null || !interfaces.isEmpty();
            DeviceStatus status = hasCoreMetrics ? DeviceStatus.UP : DeviceStatus.DEGRADED;

            return ObserviumSnmpDeviceSnapshot.builder()
                    .ipAddress(ip)
                    .hostId(ip)
                    .hostName(sysName)
                    .snmpPort(port)
                    .status(status.name())
                    .deviceStatus(status)
                    .availability(status == DeviceStatus.UP ? 1.0 : 0.5)
                    .cpuPercent(cpuAvg.isPresent() ? cpuAvg.getAsDouble() : null)
                    .memoryPercent(memUsage)
                    .uptimeSeconds(uptimeSeconds)
                    .sysDescr(sysDescr)
                    .interfaces(interfaces)
                    .collectedAtEpochSec(now)
                    .build();
        } catch (Exception exception) {
            log.debug("Observium SNMP poll failed for {}:{}: {}", ip, port, exception.getMessage());
            return ObserviumSnmpDeviceSnapshot.builder()
                    .ipAddress(ip)
                    .hostId(ip)
                    .hostName(device.getHostname() != null ? device.getHostname() : ip)
                    .snmpPort(port)
                    .status(MonitoringConstants.STATUS_DOWN)
                    .deviceStatus(DeviceStatus.DOWN)
                    .availability(0.0)
                    .cpuPercent(null)
                    .memoryPercent(null)
                    .uptimeSeconds(null)
                    .sysDescr(MonitoringConstants.UNKNOWN)
                    .interfaces(List.of())
                    .collectedAtEpochSec(now)
                    .build();
        }
    }

    private List<ObserviumInterfaceSnapshot> collectInterfaces(Snmp snmp, Target<Address> target, String hostId, long nowEpochSec) {
        Map<Integer, IfRowBuilder> rows = new HashMap<>();
        mergeWalk(rows, walkByIfIndex(snmp, target, OID_IF_DESCR), (row, value) -> row.name = value.toString());
        mergeWalk(rows, walkByIfIndex(snmp, target, OID_IF_ADMIN_STATUS), (row, value) -> row.adminStatus = adminStatus(parseInt(value)));
        mergeWalk(rows, walkByIfIndex(snmp, target, OID_IF_OPER_STATUS), (row, value) -> row.operStatus = operStatus(parseInt(value)));
        mergeWalk(rows, walkByIfIndex(snmp, target, OID_IF_IN_OCTETS), (row, value) -> row.inOctets = parseLong(value));
        mergeWalk(rows, walkByIfIndex(snmp, target, OID_IF_OUT_OCTETS), (row, value) -> row.outOctets = parseLong(value));
        mergeWalk(rows, walkByIfIndex(snmp, target, OID_IF_IN_ERRORS), (row, value) -> row.inErrors = parseLong(value));
        mergeWalk(rows, walkByIfIndex(snmp, target, OID_IF_OUT_ERRORS), (row, value) -> row.outErrors = parseLong(value));
        mergeWalk(rows, walkByIfIndex(snmp, target, OID_IF_SPEED), (row, value) -> row.speedBps = parseLong(value));

        Map<Integer, ObserviumInterface> previousByIfIndex = interfaceRepository.findByHostIdIn(List.of(hostId)).stream()
                .collect(java.util.stream.Collectors.toMap(ObserviumInterface::getIfIndex, existing -> existing, (left, right) -> left));

        List<ObserviumInterfaceSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<Integer, IfRowBuilder> entry : rows.entrySet()) {
            Integer ifIndex = entry.getKey();
            IfRowBuilder row = entry.getValue();
            if (ifIndex == null || row == null) {
                continue;
            }
            if (row.name == null || row.name.isBlank()) {
                row.name = "ifIndex-" + ifIndex;
            }

            ObserviumInterface previous = previousByIfIndex.get(ifIndex);
            InterfaceRates rates = computeRates(previous, row, nowEpochSec);
            snapshots.add(ObserviumInterfaceSnapshot.builder()
                    .ifIndex(ifIndex)
                    .name(row.name)
                    .adminStatus(row.adminStatus)
                    .operStatus(row.operStatus)
                    .inOctets(row.inOctets)
                    .outOctets(row.outOctets)
                    .inErrors(row.inErrors)
                    .outErrors(row.outErrors)
                    .speedBps(row.speedBps)
                    .inBandwidthMbps(rates.inMbps)
                    .outBandwidthMbps(rates.outMbps)
                    .utilizationPercent(rates.utilizationPercent)
                    .build());
        }
        snapshots.sort(java.util.Comparator.comparingInt(ObserviumInterfaceSnapshot::getIfIndex));
        return snapshots;
    }

    private InterfaceRates computeRates(ObserviumInterface previous, IfRowBuilder current, long nowEpochSec) {
        if (previous == null || previous.getLastPollEpochSec() == null) {
            return InterfaceRates.empty();
        }
        if (current.inOctets == null || current.outOctets == null
                || previous.getInOctets() == null || previous.getOutOctets() == null) {
            return InterfaceRates.empty();
        }

        long deltaSec = nowEpochSec - previous.getLastPollEpochSec();
        if (deltaSec <= 0) {
            return InterfaceRates.empty();
        }

        long deltaIn = safeCounterDelta(previous.getInOctets(), current.inOctets);
        long deltaOut = safeCounterDelta(previous.getOutOctets(), current.outOctets);

        double inBps = ((double) deltaIn * 8.0d) / (double) deltaSec;
        double outBps = ((double) deltaOut * 8.0d) / (double) deltaSec;
        double inMbps = inBps / 1_000_000d;
        double outMbps = outBps / 1_000_000d;

        Double utilization = null;
        if (current.speedBps != null && current.speedBps > 0) {
            double totalBps = inBps + outBps;
            utilization = Math.min(100.0d, (totalBps / (double) current.speedBps) * 100.0d);
        }

        return new InterfaceRates(round(inMbps), round(outMbps), utilization != null ? round(utilization) : null);
    }

    private long safeCounterDelta(long previous, long current) {
        if (current >= previous) {
            return current - previous;
        }
        // 32-bit counter rollover fallback.
        return (4_294_967_295L - previous) + current + 1L;
    }

    private double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }

    private void persistObservedStatus(List<ObserviumDevice> devices, List<ObserviumSnmpDeviceSnapshot> snapshots) {
        Map<String, ObserviumSnmpDeviceSnapshot> byIp = snapshots.stream()
                .collect(java.util.stream.Collectors.toMap(ObserviumSnmpDeviceSnapshot::getIpAddress, item -> item, (left, right) -> left));
        List<ObserviumDevice> toSave = new ArrayList<>(devices.size());
        for (ObserviumDevice device : devices) {
            ObserviumSnmpDeviceSnapshot snapshot = byIp.get(device.getIpAddress());
            if (snapshot == null) {
                continue;
            }
            device.setHostname(snapshot.getHostName());
            device.setStatus(snapshot.getDeviceStatus());
            device.setLastSeen(snapshot.getDeviceStatus() == DeviceStatus.DOWN ? device.getLastSeen() : Instant.now());
            toSave.add(device);
        }
        if (!toSave.isEmpty()) {
            deviceRepository.saveAll(toSave);
        }
    }

    private void persistInterfaces(List<ObserviumSnmpDeviceSnapshot> snapshots) {
        List<ObserviumInterface> entitiesToSave = new ArrayList<>();
        for (ObserviumSnmpDeviceSnapshot snapshot : snapshots) {
            List<ObserviumInterfaceSnapshot> interfaces = snapshot.getInterfaces();
            if (interfaces == null || interfaces.isEmpty()) {
                continue;
            }
            Map<Integer, ObserviumInterface> existingByIfIndex = interfaceRepository.findByHostIdIn(List.of(snapshot.getHostId())).stream()
                    .collect(java.util.stream.Collectors.toMap(ObserviumInterface::getIfIndex, existing -> existing, (left, right) -> left));

            for (ObserviumInterfaceSnapshot iface : interfaces) {
                ObserviumInterface entity = existingByIfIndex.getOrDefault(iface.getIfIndex(), new ObserviumInterface());
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

    private void logCycleSummary(List<ObserviumSnmpDeviceSnapshot> snapshots) {
        long up = snapshots.stream().filter(s -> DeviceStatus.UP == s.getDeviceStatus()).count();
        long down = snapshots.stream().filter(s -> DeviceStatus.DOWN == s.getDeviceStatus()).count();
        long degraded = snapshots.stream().filter(s -> DeviceStatus.DEGRADED == s.getDeviceStatus()).count();
        long unknown = snapshots.stream().filter(s -> DeviceStatus.UNKNOWN == s.getDeviceStatus()).count();
        long interfaces = snapshots.stream()
                .map(ObserviumSnmpDeviceSnapshot::getInterfaces)
                .filter(list -> list != null)
                .mapToLong(List::size)
                .sum();
        log.info("Observium SNMP cycle completed: total={} up={} down={} degraded={} unknown={} interfaces={}",
                snapshots.size(), up, down, degraded, unknown, interfaces);
    }

    private Target<Address> buildTarget(String ip, int port, String community) {
        CommunityTarget<Address> target = new CommunityTarget<>();
        target.setCommunity(new OctetString(community));
        target.setAddress(GenericAddress.parse("udp:" + ip + "/" + port));
        target.setVersion(SnmpConstants.version2c);
        target.setTimeout(properties.getTimeoutMs());
        target.setRetries(Math.max(0, properties.getRetries()));
        return target;
    }

    private OptionalDouble snmpWalkAverage(Snmp snmp, Target<Address> target, OID baseOid) {
        try {
            TreeUtils treeUtils = new TreeUtils(snmp, new DefaultPDUFactory(PDU.GETNEXT));
            List<TreeEvent> events = treeUtils.getSubtree(target, baseOid);
            if (events == null || events.isEmpty()) {
                return OptionalDouble.empty();
            }
            double sum = 0;
            int count = 0;
            for (TreeEvent event : events) {
                if (event == null || event.isError()) {
                    continue;
                }
                VariableBinding[] bindings = event.getVariableBindings();
                if (bindings == null) {
                    continue;
                }
                for (VariableBinding binding : bindings) {
                    try {
                        sum += Double.parseDouble(binding.getVariable().toString());
                        count++;
                    } catch (NumberFormatException ignored) {
                        // Ignore malformed values from specific devices.
                    }
                }
            }
            return count == 0 ? OptionalDouble.empty() : OptionalDouble.of(sum / count);
        } catch (Exception exception) {
            return OptionalDouble.empty();
        }
    }

    private Map<Integer, Variable> walkByIfIndex(Snmp snmp, Target<Address> target, OID baseOid) {
        Map<Integer, Variable> values = new HashMap<>();
        try {
            TreeUtils treeUtils = new TreeUtils(snmp, new DefaultPDUFactory(PDU.GETNEXT));
            List<TreeEvent> events = treeUtils.getSubtree(target, baseOid);
            if (events == null) {
                return values;
            }
            for (TreeEvent event : events) {
                if (event == null || event.isError() || event.getVariableBindings() == null) {
                    continue;
                }
                for (VariableBinding vb : event.getVariableBindings()) {
                    if (vb == null || vb.getVariable() == null || vb.isException() || vb.getOid() == null) {
                        continue;
                    }
                    int ifIndex = extractIfIndex(vb.getOid());
                    if (ifIndex <= 0) {
                        continue;
                    }
                    values.put(ifIndex, vb.getVariable());
                }
            }
        } catch (Exception ignored) {
            // Keep polling resilient for partial device support.
        }
        return values;
    }

    private int extractIfIndex(OID oid) {
        int size = oid.size();
        if (size <= 0) {
            return -1;
        }
        return oid.get(size - 1);
    }

    private void mergeWalk(Map<Integer, IfRowBuilder> rows, Map<Integer, Variable> values, IfFieldSetter setter) {
        for (Map.Entry<Integer, Variable> entry : values.entrySet()) {
            IfRowBuilder row = rows.computeIfAbsent(entry.getKey(), key -> new IfRowBuilder());
            setter.apply(row, entry.getValue());
        }
    }

    private java.util.Optional<String> snmpGetAsString(Snmp snmp, Target<Address> target, OID oid) throws IOException {
        return snmpGet(snmp, target, oid).map(Variable::toString);
    }

    private java.util.Optional<Long> snmpGetAsLong(Snmp snmp, Target<Address> target, OID oid) throws IOException {
        return snmpGet(snmp, target, oid).map(this::parseLong);
    }

    private java.util.Optional<Variable> snmpGet(Snmp snmp, Target<Address> target, OID oid) throws IOException {
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(oid));
        pdu.setType(PDU.GET);

        ResponseEvent<Address> event = snmp.get(pdu, target);
        if (event == null || event.getResponse() == null || event.getResponse().size() == 0) {
            return java.util.Optional.empty();
        }
        VariableBinding binding = event.getResponse().get(0);
        if (binding == null || binding.getVariable() == null || binding.isException()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(binding.getVariable());
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

    private int parseInt(Variable variable) {
        try {
            return Integer.parseInt(variable.toString());
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private String adminStatus(int code) {
        return switch (code) {
            case 1 -> "UP";
            case 2 -> "DOWN";
            case 3 -> "TESTING";
            default -> "UNKNOWN";
        };
    }

    private String operStatus(int code) {
        return switch (code) {
            case 1 -> "UP";
            case 2 -> "DOWN";
            case 3 -> "TESTING";
            case 4 -> "UNKNOWN";
            case 5 -> "DORMANT";
            case 6 -> "NOT_PRESENT";
            case 7 -> "LOWER_LAYER_DOWN";
            default -> "UNKNOWN";
        };
    }

    private Double computeMemoryUsagePercent(Long totalKb, Long availableKb) {
        if (totalKb == null || availableKb == null || totalKb <= 0) {
            return null;
        }
        double used = Math.max(0, totalKb - availableKb);
        return (used / totalKb) * 100.0;
    }

    private interface IfFieldSetter {
        void apply(IfRowBuilder row, Variable value);
    }

    private static final class IfRowBuilder {
        String name;
        String adminStatus;
        String operStatus;
        Long inOctets;
        Long outOctets;
        Long inErrors;
        Long outErrors;
        Long speedBps;
    }

    private static final class InterfaceRates {
        final Double inMbps;
        final Double outMbps;
        final Double utilizationPercent;

        InterfaceRates(Double inMbps, Double outMbps, Double utilizationPercent) {
            this.inMbps = inMbps;
            this.outMbps = outMbps;
            this.utilizationPercent = utilizationPercent;
        }

        static InterfaceRates empty() {
            return new InterfaceRates(null, null, null);
        }
    }
}
