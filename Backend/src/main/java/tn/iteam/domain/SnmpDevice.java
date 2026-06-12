package tn.iteam.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.enums.SnmpDeviceType;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "snmp_device")
@Getter
@Setter
public class SnmpDevice extends BaseEntity {

    @Column(name = "ip_address", unique = true, nullable = false, length = 64)
    private String ipAddress;

    @Column(length = 255)
    private String hostname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SnmpDeviceType type = SnmpDeviceType.OTHER;

    @Column(name = "snmp_port", nullable = false)
    private Integer snmpPort = 161;

    @Column(name = "snmp_community", length = 128)
    private String snmpCommunity;

    @Column(name = "snmp_version", length = 16)
    private String snmpVersion;

    @Column(length = 64)
    private String category;

    @Column(name = "device_group", length = 128)
    private String deviceGroup;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "snmp_device_metric", joinColumns = @JoinColumn(name = "snmp_device_id"))
    @Column(name = "metric_name", nullable = false, length = 64)
    private Set<String> metricsToPoll = new LinkedHashSet<>();

    @Column(name = "polling_interval_seconds", nullable = false)
    private Integer pollingIntervalSeconds = 60;

    @Column(name = "manual_entry", nullable = false)
    private Boolean manualEntry = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeviceStatus status = DeviceStatus.UNKNOWN;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(name = "last_polled_at")
    private Instant lastPolledAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_failure_at")
    private Instant lastFailureAt;

    @Column(name = "failure_count", nullable = false)
    private Integer failureCount = 0;

    @Column(nullable = false)
    private Boolean enabled = true;
}
