package tn.iteam.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class SnmpDeviceMetricsUpdateRequest {
    private Set<@Size(max = 64) String> metricsToPoll = new LinkedHashSet<>();
}
