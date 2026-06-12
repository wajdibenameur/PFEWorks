package tn.iteam.service.snmp;

import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TreeEvent;
import org.snmp4j.util.TreeUtils;
import org.springframework.stereotype.Service;
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.service.SnmpCategoryMetricsService;
import tn.iteam.util.MonitoringConstants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SnmpCategoryMetricsServiceImpl implements SnmpCategoryMetricsService {

    private static final OID OID_HR_PRINTER_STATUS = new OID("1.3.6.1.2.1.25.3.5.1.1");
    private static final OID OID_PRT_MARKER_LIFE_COUNT = new OID("1.3.6.1.2.1.43.10.2.1.4");
    private static final OID OID_PRT_SUPPLIES_MAX_CAPACITY = new OID("1.3.6.1.2.1.43.11.1.1.8");
    private static final OID OID_PRT_SUPPLIES_LEVEL = new OID("1.3.6.1.2.1.43.11.1.1.9");
    private static final OID OID_UPS_BATTERY_STATUS = new OID("1.3.6.1.2.1.33.1.2.1.0");
    private static final OID OID_UPS_BATTERY_PERCENT = new OID("1.3.6.1.2.1.33.1.2.4.0");
    private static final OID OID_UPS_RUNTIME_MINUTES = new OID("1.3.6.1.2.1.33.1.2.3.0");
    private static final OID OID_UPS_INPUT_VOLTAGE = new OID("1.3.6.1.2.1.33.1.3.3.1.3");
    private static final OID OID_UPS_OUTPUT_VOLTAGE = new OID("1.3.6.1.2.1.33.1.4.4.1.2");
    private static final OID OID_UPS_OUTPUT_LOAD_PERCENT = new OID("1.3.6.1.2.1.33.1.4.4.1.5");
    private static final OID OID_UPS_OUTPUT_FREQUENCY_TENTH_HZ = new OID("1.3.6.1.2.1.33.1.4.2.0");

    @Override
    public Map<String, Double> collectCategoryMetrics(Snmp snmp, Target<Address> target, String category) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        if (MonitoringConstants.CATEGORY_PRINTER.equals(category)) {
            snmpWalkFirstLong(snmp, target, OID_HR_PRINTER_STATUS)
                    .map(Long::doubleValue)
                    .ifPresent(value -> metrics.put("printer.status.code", value));
            snmpWalkAveragePercent(snmp, target, OID_PRT_SUPPLIES_LEVEL, OID_PRT_SUPPLIES_MAX_CAPACITY)
                    .ifPresent(value -> metrics.put("printer.toner.percent", value));
            snmpWalkFirstLong(snmp, target, OID_PRT_MARKER_LIFE_COUNT)
                    .map(Long::doubleValue)
                    .ifPresent(value -> metrics.put("printer.pages.total", value));
        }
        if (MonitoringConstants.CATEGORY_UPS.equals(category)) {
            safeSnmpGetAsLong(snmp, target, OID_UPS_BATTERY_STATUS)
                    .map(Long::doubleValue)
                    .ifPresent(value -> metrics.put("ups.battery.status", value));
            safeSnmpGetAsLong(snmp, target, OID_UPS_BATTERY_PERCENT)
                    .map(Long::doubleValue)
                    .ifPresent(value -> metrics.put("ups.battery.percent", value));
            safeSnmpGetAsLong(snmp, target, OID_UPS_RUNTIME_MINUTES)
                    .map(Long::doubleValue)
                    .ifPresent(value -> metrics.put("ups.runtime.minutes", value));
            snmpWalkAverageLong(snmp, target, OID_UPS_INPUT_VOLTAGE)
                    .ifPresent(value -> metrics.put("ups.input.voltage", value));
            snmpWalkAverageLong(snmp, target, OID_UPS_OUTPUT_VOLTAGE)
                    .ifPresent(value -> metrics.put("ups.output.voltage", value));
            snmpWalkAverageLong(snmp, target, OID_UPS_OUTPUT_LOAD_PERCENT)
                    .ifPresent(value -> metrics.put("ups.output.load.percent", value));
            safeSnmpGetAsLong(snmp, target, OID_UPS_OUTPUT_FREQUENCY_TENTH_HZ)
                    .map(value -> value / 10.0d)
                    .ifPresent(value -> metrics.put("ups.output.frequency.hz", round(value)));
        }
        return metrics;
    }

    private Optional<Double> snmpWalkAverageLong(Snmp snmp, Target<Address> target, OID baseOid) {
        List<Long> values = walkLongValues(snmp, target, baseOid);
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(round(values.stream().mapToLong(Long::longValue).average().orElse(0.0d)));
    }

    private Optional<Long> snmpWalkFirstLong(Snmp snmp, Target<Address> target, OID baseOid) {
        List<Long> values = walkLongValues(snmp, target, baseOid);
        return values.stream().findFirst();
    }

    private Optional<Double> snmpWalkAveragePercent(Snmp snmp, Target<Address> target, OID levelOid, OID maxOid) {
        List<Long> levels = walkLongValues(snmp, target, levelOid);
        List<Long> maxValues = walkLongValues(snmp, target, maxOid);
        int size = Math.min(levels.size(), maxValues.size());
        if (size == 0) {
            return Optional.empty();
        }
        double sum = 0.0d;
        int count = 0;
        for (int index = 0; index < size; index++) {
            long level = levels.get(index);
            long max = maxValues.get(index);
            if (level < 0 || max <= 0 || level > max) {
                continue;
            }
            sum += ((double) level / (double) max) * 100.0d;
            count++;
        }
        return count == 0 ? Optional.empty() : Optional.of(round(sum / count));
    }

    private List<Long> walkLongValues(Snmp snmp, Target<Address> target, OID baseOid) {
        List<Long> values = new ArrayList<>();
        for (Variable variable : walkValues(snmp, target, baseOid)) {
            Long parsed = parseLong(variable);
            if (parsed != null) {
                values.add(parsed);
            }
        }
        return values;
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

    private Optional<Long> safeSnmpGetAsLong(Snmp snmp, Target<Address> target, OID oid) {
        try {
            return snmpGetAsLong(snmp, target, oid);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private Optional<Long> snmpGetAsLong(Snmp snmp, Target<Address> target, OID oid) throws IOException {
        return snmpGet(snmp, target, oid).map(this::parseLong);
    }

    private Optional<Variable> snmpGet(Snmp snmp, Target<Address> target, OID oid) throws IOException {
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(oid));
        pdu.setType(PDU.GET);

        ResponseEvent<Address> event = snmp.get(pdu, target);
        if (event == null || event.getResponse() == null || event.getResponse().size() == 0) {
            throw new IntegrationUnavailableException(
                    MonitoringConstants.SOURCE_SNMP,
                    "No SNMP response from " + target.getAddress()
            );
        }
        VariableBinding binding = event.getResponse().get(0);
        if (binding == null || binding.getVariable() == null || binding.isException()) {
            throw new IntegrationResponseException(
                    MonitoringConstants.SOURCE_SNMP,
                    "Invalid SNMP response from " + target.getAddress()
            );
        }
        return Optional.of(binding.getVariable());
    }

    private Long parseLong(Variable variable) {
        try {
            return Long.parseLong(variable.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }
}
