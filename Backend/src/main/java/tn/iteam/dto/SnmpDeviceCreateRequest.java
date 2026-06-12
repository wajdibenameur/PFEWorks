package tn.iteam.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import tn.iteam.enums.SnmpDeviceType;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class SnmpDeviceCreateRequest {

    @NotBlank
    @Size(max = 64)
    private String ipAddress;

    @Size(max = 255)
    private String hostname;

    private SnmpDeviceType type = SnmpDeviceType.OTHER;

    @NotBlank
    @Size(max = 64)
    private String category;

    @Size(max = 128)
    private String deviceGroup;

    @Min(1)
    @Max(65535)
    private Integer snmpPort = 161;

    @Size(max = 128)
    private String snmpCommunity;

    @Pattern(regexp = "(?i)1|2c|3", message = "SNMP version must be 1, 2c or 3")
    private String snmpVersion = "2c";

    @Min(10)
    @Max(86400)
    private Integer pollingIntervalSeconds = 60;

    private Set<@Size(max = 64) String> metricsToPoll = new LinkedHashSet<>();

    private Boolean enabled = Boolean.TRUE;
}
