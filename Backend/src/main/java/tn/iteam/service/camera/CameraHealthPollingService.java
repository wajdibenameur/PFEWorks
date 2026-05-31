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
    private final ThreadPoolTaskExecutor executor;
    private final TransactionTemplate transactionTemplate;

    public CameraHealthPollingService(
            CameraDeviceRepository cameraDeviceRepository,
            CameraMonitoringProperties properties,
            SourceAvailabilityService sourceAvailabilityService,
            CameraWebSocketPublisher cameraWebSocketPublisher,
            @Qualifier("cameraPollingTaskExecutor") ThreadPoolTaskExecutor executor,
            TransactionTemplate transactionTemplate
    ) {
        this.cameraDeviceRepository = cameraDeviceRepository;
        this.properties = properties;
        this.sourceAvailabilityService = sourceAvailabilityService;
        this.cameraWebSocketPublisher = cameraWebSocketPublisher;
        this.executor = executor;
        this.transactionTemplate = transactionTemplate;
    }

    public PollingResult pollNow() {

        // Keep DB interactions short and outside long-running network scans.
        List<CameraDevice> devices = transactionTemplate.execute(
                status -> cameraDeviceRepository.findByEnabledTrue()
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
        List<CameraDeviceDTO> changed = Collections.synchronizedList(new ArrayList<>());

        int batchSize = Math.max(1,
                Math.min(properties.getPollMaxWorkers(), devices.size()));

        for (int i = 0; i < devices.size(); i += batchSize) {

            int end = Math.min(i + batchSize, devices.size());

            List<CompletableFuture<Void>> futures = devices.subList(i, end)
                    .stream()
                    .map(d -> CompletableFuture.runAsync(
                            () -> evaluate(d, now, changed), executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        List<CameraDevice> finalDevices = devices;
        transactionTemplate.executeWithoutResult(status -> cameraDeviceRepository.saveAll(finalDevices));

        updateSourceStatus(devices);

        cameraWebSocketPublisher.publishStatusChanges(changed);

        log.info("Camera poll completed: total={} changed={}",
                devices.size(), changed.size());

        return new PollingResult(devices, changed);
    }

    private void evaluate(CameraDevice device,
                          Instant now,
                          List<CameraDeviceDTO> changed) {

        try {
            DeviceStatus previous = normalize(device.getStatus());
            DeviceStatus next = resolveStatus(device);

            device.setStatus(next);

            if (next == DeviceStatus.UP) {
                device.setLastSeen(now);
            }

            if (previous != next) {
                changed.add(toDto(device));
            }

        } catch (Exception e) {
            log.warn("Camera failed ip={} port={} reason={}",
                    device.getIpAddress(), device.getPort(), safe(e));

            device.setStatus(DeviceStatus.DOWN);
        }
    }

    private DeviceStatus resolveStatus(CameraDevice device) {

        if (Boolean.FALSE.equals(device.getEnabled())) {
            return DeviceStatus.DISABLED;
        }

        for (Integer port : candidatePorts(device)) {

            if (port == null || port <= 0) continue;

            if (isReachable(device.getIpAddress(), port)) {

                if (!Objects.equals(device.getPort(), port)) {
                    device.setPort(port);
                }

                return DeviceStatus.UP;
            }
        }

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

    private boolean isReachable(String ip, int port) {

        try (Socket socket = new Socket()) {

            socket.connect(
                    new InetSocketAddress(ip, port),
                    properties.getConnectTimeoutMs()
            );

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    private void updateSourceStatus(List<CameraDevice> devices) {

        boolean up = devices.stream()
                .anyMatch(d -> normalize(d.getStatus()) == DeviceStatus.UP);

        if (up) {
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
                .source(MonitoringConstants.SOURCE_CAMERA)
                .ip(d.getIpAddress())
                .port(d.getPort())
                .status(status.name())
                .reachable(status == DeviceStatus.UP)
                .protocol(resolveProtocol(d.getPort()))
                .category(MonitoringConstants.CATEGORY_CAMERA)
                .persisted(true)
                .build();
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
