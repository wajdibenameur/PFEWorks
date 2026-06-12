package tn.iteam.service.snmp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.config.SnmpProperties;
import tn.iteam.domain.SnmpDevice;
import tn.iteam.dto.SnmpDeviceCreateRequest;
import tn.iteam.dto.SnmpDeviceMetricsUpdateRequest;
import tn.iteam.dto.SnmpDeviceResponseDTO;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.enums.SnmpDeviceType;
import tn.iteam.exception.IntegrationDataUnavailableException;
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.repository.SnmpDeviceRepository;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SnmpDeviceManagementService {

    private static final String SOURCE = "SNMP";

    private final SnmpDeviceRepository deviceRepository;
    private final SnmpProperties snmpProperties;

    @Transactional(readOnly = true)
    public List<SnmpDeviceResponseDTO> listDevices() {
        return deviceRepository.findAllByOrderByIpAddressAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SnmpDeviceResponseDTO getDevice(Long id) {
        return toResponse(getRequired(id));
    }

    @Transactional
    public SnmpDeviceResponseDTO createDevice(SnmpDeviceCreateRequest request) {
        String normalizedIp = normalize(request.getIpAddress());
        deviceRepository.findByIpAddress(normalizedIp).ifPresent(existing -> {
            throw new IntegrationResponseException(SOURCE, "SNMP device already exists for IP " + normalizedIp);
        });

        SnmpDevice device = new SnmpDevice();
        device.setManualEntry(true);
        device.setStatus(DeviceStatus.UNKNOWN);
        applyRequest(device, normalizedIp, request);
        return toResponse(deviceRepository.save(device));
    }

    @Transactional
    public SnmpDeviceResponseDTO updateDevice(Long id, SnmpDeviceCreateRequest request) {
        SnmpDevice device = getRequired(id);
        String normalizedIp = normalize(request.getIpAddress());
        deviceRepository.findByIpAddress(normalizedIp)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IntegrationResponseException(SOURCE, "SNMP device already exists for IP " + normalizedIp);
                });

        applyRequest(device, normalizedIp, request);
        return toResponse(deviceRepository.save(device));
    }

    @Transactional
    public SnmpDeviceResponseDTO updateEnabled(Long id, boolean enabled) {
        SnmpDevice device = getRequired(id);
        device.setEnabled(enabled);
        if (!enabled) {
            device.setStatus(DeviceStatus.DISABLED);
        } else if (device.getStatus() == DeviceStatus.DISABLED) {
            device.setStatus(DeviceStatus.UNKNOWN);
        }
        return toResponse(deviceRepository.save(device));
    }

    @Transactional
    public SnmpDeviceResponseDTO updateMetrics(Long id, SnmpDeviceMetricsUpdateRequest request) {
        SnmpDevice device = getRequired(id);
        device.setMetricsToPoll(normalizeMetrics(request.getMetricsToPoll()));
        return toResponse(deviceRepository.save(device));
    }

    @Transactional
    public void deleteDevice(Long id, boolean hardDelete) {
        SnmpDevice device = getRequired(id);
        if (hardDelete) {
            deviceRepository.delete(device);
            return;
        }
        device.setEnabled(false);
        device.setStatus(DeviceStatus.DISABLED);
        device.setLastFailureAt(Instant.now());
        deviceRepository.save(device);
    }

    private void applyRequest(SnmpDevice device, String normalizedIp, SnmpDeviceCreateRequest request) {
        device.setIpAddress(normalizedIp);
        device.setHostname(normalizeNullable(request.getHostname(), normalizedIp));
        device.setType(request.getType() != null ? request.getType() : SnmpDeviceType.OTHER);
        device.setCategory(normalizeCategory(request.getCategory()));
        device.setDeviceGroup(normalizeNullable(request.getDeviceGroup(), null));
        device.setSnmpPort(request.getSnmpPort() != null ? request.getSnmpPort() : snmpProperties.getDefaultPort());
        device.setSnmpCommunity(normalizeNullable(request.getSnmpCommunity(), snmpProperties.getDefaultCommunity()));
        device.setSnmpVersion(normalizeNullable(request.getSnmpVersion(), snmpProperties.getDefaultVersion()));
        device.setPollingIntervalSeconds(request.getPollingIntervalSeconds() != null
                ? request.getPollingIntervalSeconds()
                : snmpProperties.getDefaultPollingIntervalSeconds());
        device.setMetricsToPoll(normalizeMetrics(request.getMetricsToPoll()));
        device.setEnabled(request.getEnabled() == null || request.getEnabled());
        if (Boolean.FALSE.equals(device.getEnabled())) {
            device.setStatus(DeviceStatus.DISABLED);
        } else if (device.getStatus() == DeviceStatus.DISABLED) {
            device.setStatus(DeviceStatus.UNKNOWN);
        }
    }

    private SnmpDeviceResponseDTO toResponse(SnmpDevice device) {
        return SnmpDeviceResponseDTO.builder()
                .id(device.getId())
                .ipAddress(device.getIpAddress())
                .hostname(device.getHostname())
                .type(device.getType())
                .category(device.getCategory())
                .deviceGroup(device.getDeviceGroup())
                .snmpPort(device.getSnmpPort())
                .snmpCommunity(device.getSnmpCommunity())
                .snmpVersion(device.getSnmpVersion())
                .pollingIntervalSeconds(device.getPollingIntervalSeconds())
                .metricsToPoll(device.getMetricsToPoll() != null ? Set.copyOf(device.getMetricsToPoll()) : Set.of())
                .status(device.getStatus() != null ? device.getStatus().name() : null)
                .lastSeen(device.getLastSeen())
                .lastPolledAt(device.getLastPolledAt())
                .lastSuccessAt(device.getLastSuccessAt())
                .lastFailureAt(device.getLastFailureAt())
                .failureCount(device.getFailureCount())
                .createdAt(device.getCreatedAt())
                .updatedAt(device.getUpdatedAt())
                .enabled(device.getEnabled())
                .manualEntry(device.getManualEntry())
                .build();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeNullable(String value, String fallback) {
        if (value == null || value.trim().isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String normalizeCategory(String value) {
        String normalized = normalizeNullable(value, null);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private Set<String> normalizeMetrics(Set<String> metrics) {
        Set<String> normalized = new LinkedHashSet<>();
        if (metrics == null || metrics.isEmpty()) {
            normalized.addAll(snmpProperties.getDefaultMetricsToPoll());
            return normalized;
        }
        metrics.stream()
                .map(this::normalize)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .forEach(normalized::add);
        if (normalized.isEmpty()) {
            normalized.addAll(snmpProperties.getDefaultMetricsToPoll());
        }
        return normalized;
    }

    private SnmpDevice getRequired(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> IntegrationDataUnavailableException.forSnmp("SNMP device not found: " + id));
    }
}
