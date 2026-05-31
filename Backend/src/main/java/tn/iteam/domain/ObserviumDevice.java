package tn.iteam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import tn.iteam.enums.DeviceStatus;

import java.time.Instant;

@Entity
@Table(name = "observium_device")
@Getter
@Setter

public class ObserviumDevice extends BaseEntity {

    @Column(name = "ip_address", unique = true, nullable = false, length = 64)
    private String ipAddress;

    @Column(length = 255)
    private String hostname;

    @Column(name = "snmp_port", nullable = false)
    private Integer snmpPort = 161;

    @Column(name = "snmp_community", length = 128)
    private String snmpCommunity;

    @Column(name = "snmp_version", length = 16)
    private String snmpVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeviceStatus status = DeviceStatus.UNKNOWN;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(nullable = false)
    private Boolean enabled = true;
}