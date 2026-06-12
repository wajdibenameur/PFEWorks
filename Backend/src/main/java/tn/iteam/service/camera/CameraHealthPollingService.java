package tn.iteam.service.camera;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tn.iteam.config.CameraMonitoringProperties;
import tn.iteam.domain.CameraDevice;
import tn.iteam.dto.CameraDeviceDTO;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.repository.CameraDeviceRepository;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.support.DatabasePersistenceGuard;
import tn.iteam.util.MonitoringConstants;
import tn.iteam.websocket.CameraWebSocketPublisher;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service

public class CameraHealthPollingService {

    private final CameraDeviceRepository cameraDeviceRepository;
    private final CameraMonitoringProperties properties;
    private final SourceAvailabilityService sourceAvailabilityService;
    private final CameraWebSocketPublisher cameraWebSocketPublisher;
    private final CameraReachabilityService cameraReachabilityService;
    private final ThreadPoolTaskExecutor executor;
    private final TransactionTemplate transactionTemplate;
    private final DatabasePersistenceGuard databasePersistenceGuard;

    public CameraHealthPollingService(
            CameraDeviceRepository cameraDeviceRepository,
            CameraMonitoringProperties properties,
            SourceAvailabilityService sourceAvailabilityService,
            CameraWebSocketPublisher cameraWebSocketPublisher,
            CameraReachabilityService cameraReachabilityService,
            @Qualifier("cameraPollingTaskExecutor") ThreadPoolTaskExecutor executor,
            TransactionTemplate transactionTemplate,
            DatabasePersistenceGuard databasePersistenceGuard
    ) {
        this.cameraDeviceRepository = cameraDeviceRepository;
        this.properties = properties;
        this.sourceAvailabilityService = sourceAvailabilityService;
        this.cameraWebSocketPublisher = cameraWebSocketPublisher;
        this.cameraReachabilityService = cameraReachabilityService;
        this.executor = executor;
        this.transactionTemplate = transactionTemplate;
        this.databasePersistenceGuard = databasePersistenceGuard;
    }

    public PollingResult pollNow() {

        // Keep DB interactions short and outside long-running network scans.
        List<CameraDevice> devices = databasePersistenceGuard.safeLoad(
                MonitoringConstants.SOURCE_CAMERA,
                "camera-device-load",
                () -> transactionTemplate.execute(status -> cameraDeviceRepository.findByEnabledTrue()),
                List.of()
        );
        if (devices == null) {
            devices = List.of();
        }

        if (devices.isEmpty()) {
            sourceAvailabilityService.markDegraded(
                    MonitoringConstants.SOURCE_CAMERA,
                    "No enabled cameras"
            );
            return new PollingResult(List.of(), List.of());
        }

        Instant now = Instant.now();
        List<CameraDeviceDTO> updates = Collections.synchronizedList(new ArrayList<>());

        int batchSize = Math.max(1,
                Math.min(properties.getPollMaxWorkers(), devices.size()));

        for (int i = 0; i < devices.size(); i += batchSize) {

            int end = Math.min(i + batchSize, devices.size());

            List<CompletableFuture<Void>> futures = devices.subList(i, end)
                    .stream()
                    .map(d -> CompletableFuture.runAsync(
                            () -> evaluate(d, now, updates), executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        List<CameraDevice> finalDevices = devices;
        databasePersistenceGuard.safeRun(
                MonitoringConstants.SOURCE_CAMERA,
                "camera-device-save",
                () -> transactionTemplate.executeWithoutResult(status -> cameraDeviceRepository.saveAll(finalDevices))
        );
        if (properties.isLogProbeDetails()) {
            finalDevices.forEach(device -> log.info(
                    "Camera status saved ip={} port={} status={} lastCheckedAt={} lastSeen={}",
                    device.getIpAddress(),
                    device.getPort(),
                    device.getStatus(),
                    device.getLastCheckedAt(),
                    device.getLastSeen()
            ));
        }

        updateSourceStatus(devices);

        cameraWebSocketPublisher.publishStatusChanges(updates);

        log.info("Camera poll completed: total={} changed={}",
                devices.size(), updates.size());

        return new PollingResult(devices, updates);
    }

    private void evaluate(CameraDevice device,
                          Instant now,
                          List<CameraDeviceDTO> updates) {

        try {
            DeviceStatus previous = normalize(device.getStatus());
            DeviceStatus next = resolveStatus(device);

            device.setStatus(next);
            device.setLastCheckedAt(now);

            if (next == DeviceStatus.UP || next == DeviceStatus.DEGRADED) {
                device.setLastSeen(now);
            }

            CameraDeviceDTO update = toDto(device);
            if (previous != next || update.getLastScanAt() != null) {
                updates.add(update);
            }

        } catch (Exception e) {
            log.warn("Camera failed ip={} port={} reason={}",
                    device.getIpAddress(), device.getPort(), safe(e));

            device.setStatus(DeviceStatus.DOWN);
            device.setLastCheckedAt(now);
            updates.add(toDto(device));
        }
    }

    private DeviceStatus resolveStatus(CameraDevice device) {

        if (Boolean.FALSE.equals(device.getEnabled())) {
            return DeviceStatus.DISABLED;
        }
        List<Integer> ports = candidatePorts(device);
        CameraReachabilityService.CameraProbeResult probeResult =
                cameraReachabilityService.probe(device.getIpAddress(), ports);

        if (probeResult.reachable()) {
            if (probeResult.selectedPort() != null && !Objects.equals(device.getPort(), probeResult.selectedPort())) {
                device.setPort(probeResult.selectedPort());
            }
            DeviceStatus resolvedStatus = "TCP_CONNECT".equals(probeResult.method())
                    ? DeviceStatus.UP
                    : DeviceStatus.DEGRADED;
            log.info(
                    "Camera computed status ip={} timeoutMs={} rawResult={} calculatedStatus={}",
                    device.getIpAddress(),
                    probeResult.timeoutMs(),
                    probeResult.attemptLogs(),
                    resolvedStatus
            );
            return resolvedStatus;
        }

        log.warn(
                "Camera computed status ip={} timeoutMs={} rawResult={} calculatedStatus=DOWN",
                device.getIpAddress(),
                probeResult.timeoutMs(),
                probeResult.attemptLogs()
        );
        return DeviceStatus.DOWN;
    }

    private List<Integer> candidatePorts(CameraDevice device) {

        List<Integer> config = properties.getPorts() == null
                ? List.of()
                : properties.getPorts();

        Integer preferred = device.getPort();

        if (preferred == null || preferred <= 0) return config;

        if (config.contains(preferred)) return config;

        List<Integer> merged = new ArrayList<>();
        merged.add(preferred);
        merged.addAll(config);

        return merged;
    }

    private void updateSourceStatus(List<CameraDevice> devices) {

        boolean available = devices.stream()
                .anyMatch(d -> {
                    DeviceStatus status = normalize(d.getStatus());
                    return status == DeviceStatus.UP || status == DeviceStatus.DEGRADED;
                });

        if (available) {
            sourceAvailabilityService.markAvailable(MonitoringConstants.SOURCE_CAMERA);
        } else {
            sourceAvailabilityService.markDegraded(
                    MonitoringConstants.SOURCE_CAMERA,
                    "No reachable cameras"
            );
        }
    }

    private DeviceStatus normalize(DeviceStatus status) {
        return status == null ? DeviceStatus.UNKNOWN : status;
    }

    public CameraDeviceDTO toDto(CameraDevice d) {

        DeviceStatus status = normalize(d.getStatus());

        return CameraDeviceDTO.builder()
                .id(d.getId())
                .source(MonitoringConstants.SOURCE_CAMERA)
                .name(d.getName() != null && !d.getName().isBlank() ? d.getName() : "Camera")
                .site(d.getSite())
                .type(d.getType())
                .ip(d.getIpAddress())
                .port(d.getPort())
                .status(status.name())
                .reachable(status == DeviceStatus.UP || status == DeviceStatus.DEGRADED)
                .protocol(resolveProtocol(d.getPort()))
                .category(MonitoringConstants.CATEGORY_CAMERA)
                .lastScanAt(toLocalDateTime(d.getLastCheckedAt() != null ? d.getLastCheckedAt() : d.getLastSeen()))
                .persisted(true)
                .enabled(Boolean.TRUE.equals(d.getEnabled()))
                .createdAt(toLocalDateTime(d.getCreatedAt()))
                .updatedAt(toLocalDateTime(d.getUpdatedAt()))
                .build();
    }

    private java.time.LocalDateTime toLocalDateTime(Instant value) {
        if (value == null) {
            return null;
        }
        return java.time.LocalDateTime.ofInstant(value, java.time.ZoneId.systemDefault());
    }

    private String resolveProtocol(Integer port) {
        return switch (port == null ? -1 : port) {
            case 554 -> MonitoringConstants.PROTOCOL_RTSP;
            case 80, 8080 -> MonitoringConstants.PROTOCOL_HTTP;
            default -> "TCP";
        };
    }

    public record PollingResult(
            List<CameraDevice> devices,
            List<CameraDeviceDTO> changedDevices
    ) {}

    private String safe(Throwable t) {
        return t == null ? "unknown" :
                (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
    }
}
