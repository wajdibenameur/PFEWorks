package tn.iteam.service.camera;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.config.CameraMonitoringProperties;
import tn.iteam.domain.CameraDevice;
import tn.iteam.dto.CameraDeviceDTO;
import tn.iteam.dto.CameraDeviceUpsertRequest;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.exception.IntegrationDataUnavailableException;
import tn.iteam.exception.IntegrationResponseException;
import tn.iteam.repository.CameraDeviceRepository;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CameraDeviceManagementService {

    private static final String SOURCE = "CAMERA";

    private final CameraDeviceRepository cameraDeviceRepository;
    private final CameraMonitoringProperties cameraMonitoringProperties;

    @Transactional
    public CameraDeviceDTO createDevice(CameraDeviceUpsertRequest request) {
        String normalizedIp = normalizeIp(request.getIpAddress());
        cameraDeviceRepository.findByIpAddress(normalizedIp).ifPresent(existing -> {
            throw new IntegrationResponseException(SOURCE, "Camera already exists for IP " + normalizedIp);
        });

        CameraDevice device = new CameraDevice();
        device.setStatus(DeviceStatus.UNKNOWN);
        applyRequest(device, normalizedIp, request);
        return toResponse(cameraDeviceRepository.save(device));
    }

    @Transactional
    public CameraDeviceDTO updateDevice(Long id, CameraDeviceUpsertRequest request) {
        CameraDevice device = getRequired(id);
        String normalizedIp = normalizeIp(request.getIpAddress());
        cameraDeviceRepository.findByIpAddress(normalizedIp)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IntegrationResponseException(SOURCE, "Camera already exists for IP " + normalizedIp);
                });

        applyRequest(device, normalizedIp, request);
        return toResponse(cameraDeviceRepository.save(device));
    }

    @Transactional
    public CameraDeviceDTO updateEnabled(Long id, boolean enabled) {
        CameraDevice device = getRequired(id);
        device.setEnabled(enabled);
        if (!enabled) {
            device.setStatus(DeviceStatus.DISABLED);
        } else if (device.getStatus() == DeviceStatus.DISABLED) {
            device.setStatus(DeviceStatus.UNKNOWN);
        }
        return toResponse(cameraDeviceRepository.save(device));
    }

    @Transactional
    public void deleteDevice(Long id) {
        CameraDevice device = getRequired(id);
        cameraDeviceRepository.delete(device);
    }

    private void applyRequest(CameraDevice device, String normalizedIp, CameraDeviceUpsertRequest request) {
        device.setIpAddress(normalizedIp);
        device.setSubnet(resolveSubnet(normalizedIp));
        device.setName(normalizeNullable(request.getName(), "Camera " + normalizedIp));
        device.setSite(normalizeNullable(request.getSite(), null));
        device.setType(normalizeNullable(request.getType(), "IP_CAMERA"));
        device.setPort(request.getPort() != null ? request.getPort() : resolveDefaultPort());
        device.setEnabled(request.getEnabled() == null || request.getEnabled());
        if (Boolean.FALSE.equals(device.getEnabled())) {
            device.setStatus(DeviceStatus.DISABLED);
        } else if (device.getStatus() == DeviceStatus.DISABLED) {
            device.setStatus(DeviceStatus.UNKNOWN);
        }
    }

    private CameraDeviceDTO toResponse(CameraDevice device) {
        DeviceStatus status = device.getStatus() != null ? device.getStatus() : DeviceStatus.UNKNOWN;
        return CameraDeviceDTO.builder()
                .id(device.getId())
                .source(SOURCE)
                .name(normalizeNullable(device.getName(), "Camera"))
                .site(device.getSite())
                .type(device.getType())
                .ip(device.getIpAddress())
                .port(device.getPort())
                .protocol(resolveProtocol(device.getPort()))
                .status(status.name())
                .category("CAMERA")
                .lastScanAt(toLocalDateTime(device.getLastCheckedAt() != null ? device.getLastCheckedAt() : device.getLastSeen()))
                .reachable(status == DeviceStatus.UP || status == DeviceStatus.DEGRADED)
                .persisted(true)
                .snmpEnabled(false)
                .enabled(Boolean.TRUE.equals(device.getEnabled()))
                .createdAt(toLocalDateTime(device.getCreatedAt()))
                .updatedAt(toLocalDateTime(device.getUpdatedAt()))
                .build();
    }

    private CameraDevice getRequired(Long id) {
        return cameraDeviceRepository.findById(id)
                .orElseThrow(() -> IntegrationDataUnavailableException.forCamera("Camera not found: " + id));
    }

    private String normalizeIp(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeNullable(String value, String fallback) {
        if (value == null || value.trim().isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private Integer resolveDefaultPort() {
        List<Integer> ports = cameraMonitoringProperties.getPorts();
        if (ports == null) {
            return null;
        }
        return ports.stream().filter(port -> port != null && port > 0).findFirst().orElse(null);
    }

    private String resolveSubnet(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }
        int index = ipAddress.lastIndexOf('.');
        return index > 0 ? ipAddress.substring(0, index) : ipAddress;
    }

    private String resolveProtocol(Integer port) {
        return switch (port == null ? -1 : port) {
            case 554 -> "RTSP";
            case 80, 8080 -> "HTTP";
            default -> "TCP";
        };
    }

    private java.time.LocalDateTime toLocalDateTime(Instant value) {
        if (value == null) {
            return null;
        }
        return java.time.LocalDateTime.ofInstant(value, ZoneId.systemDefault());
    }
}
