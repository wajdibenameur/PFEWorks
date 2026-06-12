package tn.iteam.service;

import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.smi.Address;
import tn.iteam.adapter.snmp.SnmpInterfaceSnapshot;

import java.util.List;

public interface SnmpInterfaceCollectionService {

    List<SnmpInterfaceSnapshot> collectInterfaces(Snmp snmp, Target<Address> target, String hostId, long nowEpochSec);
}
