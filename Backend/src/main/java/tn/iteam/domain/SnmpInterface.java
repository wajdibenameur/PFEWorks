package tn.iteam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "snmp_interface",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_snmp_interface_host_ifindex", columnNames = {"hostId", "ifIndex"})
        }
)
@Getter
@Setter
public class SnmpInterface extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String hostId;

    @Column(length = 64)
    private String ipAddress;

    @Column(nullable = false)
    private Integer ifIndex;

    @Column(length = 255)
    private String name;

    @Column(length = 32)
    private String adminStatus;

    @Column(length = 32)
    private String operStatus;

    private Long inOctets;
    private Long outOctets;
    private Long inErrors;
    private Long outErrors;
    private Long speedBps;

    private Double inBandwidthMbps;
    private Double outBandwidthMbps;
    private Double utilizationPercent;

    @Column(nullable = false)
    private Long lastPollEpochSec;
}
