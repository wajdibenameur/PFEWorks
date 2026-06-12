package tn.iteam.service.snmp;

import lombok.RequiredArgsConstructor;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TreeEvent;
import org.snmp4j.util.TreeUtils;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.snmp.SnmpInterfaceSnapshot;
import tn.iteam.domain.SnmpInterface;
import tn.iteam.service.SnmpInterfaceCollectionService;
import tn.iteam.service.SnmpObservedStateService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SnmpInterfaceCollectionServiceImpl implements SnmpInterfaceCollectionService {

    private static final OID OID_IF_DESCR = new OID("1.3.6.1.2.1.2.2.1.2");
    private static final OID OID_IF_ADMIN_STATUS = new OID("1.3.6.1.2.1.2.2.1.7");
    private static final OID OID_IF_OPER_STATUS = new OID("1.3.6.1.2.1.2.2.1.8");
    private static final OID OID_IF_IN_OCTETS = new OID("1.3.6.1.2.1.2.2.1.10");
    private static final OID OID_IF_IN_ERRORS = new OID("1.3.6.1.2.1.2.2.1.14");
    private static final OID OID_IF_OUT_OCTETS = new OID("1.3.6.1.2.1.2.2.1.16");
    private static final OID OID_IF_OUT_ERRORS = new OID("1.3.6.1.2.1.2.2.1.20");
    private static final OID OID_IF_SPEED = new OID("1.3.6.1.2.1.2.2.1.5");
    private static final OID OID_IF_HC_IN_OCTETS = new OID("1.3.6.1.2.1.31.1.1.1.6");
    private static final OID OID_IF_HC_OUT_OCTETS = new OID("1.3.6.1.2.1.31.1.1.1.10");

    private final SnmpObservedStateService observedStateService;

    @Override
    public List<SnmpInterfaceSnapshot> collectInterfaces(Snmp snmp, Target<Address> target, String hostId, long nowEpochSec) {
        Map<Integer, IfRowBuilder> rows = new HashMap<>();
        mergeWalk(rows, walkByIfIndex(snmp, target, OID_IF_DESCR), (row, value) -> row.name = value.toString());
        mergeWalk(rows, walkByIfIndex(snmp, target, OID_IF_ADMIN_STATUS), (row, value) -> row.adminStatus = adminStatus(parseInt(value)));
        mergeWalk(rows, walkByIfIndex(snmp, target, OID_IF_OPER_STATUS), (row, value) -> row.operStatus = operStatus(parseInt(value)));
        Map<Integer, Variable> inOctets = walkByIfIndex(snmp, target, OID_IF_HC_IN_OCTETS);
        if (inOctets.isEmpty()) {
            inOctets = walkByIfIndex(snmp, target, OID_IF_IN_OCTETS);
        }
        Map<Integer, Variable> outOctets = walkByIfIndex(snmp, target, OID_IF_HC_OUT_OCTETS);
        if (outOctets.isEmpty()) {
            outOctets = walkByIfIndex(snmp, target, OID_IF_OUT_OCTETS);
        }
        mergeWalk(rows, inOctets, (row, value) -> row.inOctets = parseLong(value));
        mergeWalk(rows, outOctets, (row, value) -> row.outOctets = parseLong(value));
        mergeWalk(rows, walkByIfIndex(snmp, target, OID_IF_IN_ERRORS), (row, value) -> row.inErrors = parseLong(value));
        mergeWalk(rows, walkByIfIndex(snmp, target, OID_IF_OUT_ERRORS), (row, value) -> row.outErrors = parseLong(value));
        mergeWalk(rows, walkByIfIndex(snmp, target, OID_IF_SPEED), (row, value) -> row.speedBps = parseLong(value));

        Map<Integer, SnmpInterface> previousByIfIndex = observedStateService.loadPreviousInterfacesByIndex(hostId);

        List<SnmpInterfaceSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<Integer, IfRowBuilder> entry : rows.entrySet()) {
            Integer ifIndex = entry.getKey();
            IfRowBuilder row = entry.getValue();
            if (ifIndex == null || row == null) {
                continue;
            }
            if (row.name == null || row.name.isBlank()) {
                row.name = "ifIndex-" + ifIndex;
            }

            SnmpInterface previous = previousByIfIndex.get(ifIndex);
            InterfaceRates rates = computeRates(previous, row, nowEpochSec);
            snapshots.add(SnmpInterfaceSnapshot.builder()
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
                    .recentInErrors(rates.recentInErrors)
                    .recentOutErrors(rates.recentOutErrors)
                    .build());
        }
        snapshots.sort(Comparator.comparingInt(SnmpInterfaceSnapshot::getIfIndex));
        return snapshots;
    }

    private InterfaceRates computeRates(SnmpInterface previous, IfRowBuilder current, long nowEpochSec) {
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
        Long deltaInErrors = null;
        if (current.inErrors != null && previous.getInErrors() != null) {
            deltaInErrors = safeCounterDelta(previous.getInErrors(), current.inErrors);
        }
        Long deltaOutErrors = null;
        if (current.outErrors != null && previous.getOutErrors() != null) {
            deltaOutErrors = safeCounterDelta(previous.getOutErrors(), current.outErrors);
        }

        double inBps = ((double) deltaIn * 8.0d) / (double) deltaSec;
        double outBps = ((double) deltaOut * 8.0d) / (double) deltaSec;
        double inMbps = inBps / 1_000_000d;
        double outMbps = outBps / 1_000_000d;

        Double utilization = null;
        if (current.speedBps != null && current.speedBps > 0) {
            double totalBps = inBps + outBps;
            utilization = Math.min(100.0d, (totalBps / (double) current.speedBps) * 100.0d);
        }

        return new InterfaceRates(
                round(inMbps),
                round(outMbps),
                utilization != null ? round(utilization) : null,
                deltaInErrors,
                deltaOutErrors
        );
    }

    private long safeCounterDelta(long previous, long current) {
        if (current >= previous) {
            return current - previous;
        }
        return (4_294_967_295L - previous) + current + 1L;
    }

    private double round(double value) {
        return Math.round(value * 1000d) / 1000d;
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
        final Long recentInErrors;
        final Long recentOutErrors;

        InterfaceRates(Double inMbps, Double outMbps, Double utilizationPercent, Long recentInErrors, Long recentOutErrors) {
            this.inMbps = inMbps;
            this.outMbps = outMbps;
            this.utilizationPercent = utilizationPercent;
            this.recentInErrors = recentInErrors;
            this.recentOutErrors = recentOutErrors;
        }

        static InterfaceRates empty() {
            return new InterfaceRates(null, null, null, null, null);
        }
    }
}
