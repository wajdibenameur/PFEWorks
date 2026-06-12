package tn.iteam.adapter.snmp;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SnmpInterfaceSnapshot {
    int ifIndex;
    String name;
    String adminStatus;
    String operStatus;
    Long inOctets;
    Long outOctets;
    Long inErrors;
    Long outErrors;
    Long speedBps;
    Double inBandwidthMbps;
    Double outBandwidthMbps;
    Double utilizationPercent;
    Long recentInErrors;
    Long recentOutErrors;
}
