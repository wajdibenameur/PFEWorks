package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tn.iteam.domain.CameraDevice;
import tn.iteam.domain.SnmpDevice;
import tn.iteam.domain.SnmpMetric;
import tn.iteam.dto.CameraDeviceDTO;
import tn.iteam.repository.CameraDeviceRepository;
import tn.iteam.repository.SnmpDeviceRepository;
import tn.iteam.repository.SnmpMetricRepository;
import tn.iteam.service.CameraInventoryService;
import tn.iteam.service.camera.CameraHealthPollingService;
import tn.iteam.util.MonitoringConstants;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(
        name = "app.db.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@RequiredArgsConstructor
public class CameraInventoryServiceImpl implements CameraInventoryService {

    private final CameraDeviceRepository cameraDeviceRepository;
    private final CameraHealthPollingService cameraHealthPollingService;
    private final SnmpDeviceRepository snmpDeviceRepository;
    private final SnmpMetricRepository snmpMetricRepository;

    @Override
    public List<CameraDeviceDTO> getRegisteredCameras() {
        List<CameraDevice> cameras = cameraDeviceRepository.findAll();
        if (cameras.isEmpty()) {
            return List.of();
        }
        Set<String> ips = cameras.stream()
                .map(CameraDevice::getIpAddress)
                .filter(ip -> ip != null && !ip.isBlank())
                .collect(Collectors.toSet());
        Map<String, SnmpDevice> snmpDevicesByIp = snmpDeviceRepository.findByIpAddressInOrderByIpAddressAsc(ips).stream()
                .filter(device -> MonitoringConstants.CATEGORY_CAMERA.equalsIgnoreCase(device.getCategory()))
                .collect(Collectors.toMap(SnmpDevice::getIpAddress, device -> device, (left, right) -> left, LinkedHashMap::new));
        Map<String, Map<String, SnmpMetric>> latestMetricsByIp = new LinkedHashMap<>();
        for (SnmpMetric metric : snmpMetricRepository.findByIpInOrderByTimestampDesc(ips)) {
            String ip = metric.getIp();
            if (ip == null || !snmpDevicesByIp.containsKey(ip)) {
                continue;
            }
            latestMetricsByIp.computeIfAbsent(ip, ignored -> new LinkedHashMap<>())
                    .putIfAbsent(metric.getMetricKey(), metric);
        }

        return cameras.stream()
                .sorted(Comparator.comparing(CameraDevice::getIpAddress, Comparator.nullsLast(String::compareTo))
                        .thenComparing(CameraDevice::getPort, Comparator.nullsLast(Integer::compareTo)))
                .map(cameraHealthPollingService::toDto)
                .map(dto -> enrichWithSnmp(dto, snmpDevicesByIp.get(dto.getIp()), latestMetricsByIp.get(dto.getIp())))
                .toList();
    }

    private CameraDeviceDTO enrichWithSnmp(CameraDeviceDTO dto, SnmpDevice snmpDevice, Map<String, SnmpMetric> metricsByKey) {
        if (dto == null || snmpDevice == null) {
            return dto;
        }

        dto.setSnmpEnabled(Boolean.TRUE.equals(snmpDevice.getEnabled()));
        dto.setSnmpStatus(snmpDevice.getStatus() != null ? snmpDevice.getStatus().name() : null);
        dto.setSnmpSysName(snmpDevice.getHostname());
        dto.setSnmpLastSeenAt(snmpDevice.getLastSeen() != null
                ? java.time.LocalDateTime.ofInstant(snmpDevice.getLastSeen(), java.time.ZoneId.systemDefault())
                : null);

        Map<String, SnmpMetric> safeMetrics = metricsByKey != null ? metricsByKey : Map.of();
        dto.setSnmpUptimeSeconds(asLong(safeMetrics.get("snmp.uptime.seconds")));
        dto.setSnmpCpuPercent(asDouble(safeMetrics.get("snmp.cpu.percent")));
        dto.setSnmpMemoryPercent(asDouble(safeMetrics.get("snmp.memory.percent")));
        dto.setSnmpInterfaceCount((int) safeMetrics.keySet().stream()
                .filter(key -> key != null && key.startsWith("snmp.interface."))
                .map(key -> {
                    String[] parts = key.split("\\.");
                    return parts.length > 2 ? parts[2] : null;
                })
                .filter(index -> index != null && !index.isBlank())
                .distinct()
                .count());
        return dto;
    }

    private Double asDouble(SnmpMetric metric) {
        return metric != null ? metric.getValue() : null;
    }

    private Long asLong(SnmpMetric metric) {
        return metric != null && metric.getValue() != null ? metric.getValue().longValue() : null;
    }
}
