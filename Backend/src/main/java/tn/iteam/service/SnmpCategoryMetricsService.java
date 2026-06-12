package tn.iteam.service;

import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.smi.Address;

import java.util.Map;

public interface SnmpCategoryMetricsService {

    Map<String, Double> collectCategoryMetrics(Snmp snmp, Target<Address> target, String category);
}
