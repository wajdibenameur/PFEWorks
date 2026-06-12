package tn.iteam.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tn.iteam.domain.CameraDevice;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.snapshot.SnapshotStore;
import tn.iteam.monitoring.snapshot.StoredSnapshot;
import tn.iteam.service.camera.CameraHealthPollingService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraIntegrationService implements AsyncIntegrationService {

    private static final String DATASET_HOSTS = "hosts";
    private static final String SOURCE = MonitoringSourceType.CAMERA.name();

    private final CameraHealthPollingService cameraHealthPollingService;
    private final SnapshotStore snapshotStore;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public MonitoringSourceType getSourceType() {
        return MonitoringSourceType.CAMERA;
    }

    @Override
    public void refresh() {
        subscribeSafely("refresh", refreshHostsAsync());
    }

    @Override
    public void refreshHosts() {
        subscribeSafely("refreshHosts", refreshHostsAsync());
    }

    @Override
    public Mono<Void> refreshAsync() {
        return refreshHostsAsync();
    }

    @Override
    public Mono<Void> refreshHostsAsync() {

        return Mono.defer(() -> {

            if (!running.compareAndSet(false, true)) {
                log.debug("Camera refresh already running");
                return Mono.empty();
            }

            return Mono.fromCallable(cameraHealthPollingService::pollNow)
                    .subscribeOn(Schedulers.boundedElastic())

                    .map(result -> result.devices()
                            .stream()
                            .map(this::toHost)
                            .toList()
                    )

                    .doOnNext(this::safeSaveSnapshot)

                    .onErrorResume(e -> {
                        log.warn("Camera polling failed: {}", safeMessage(e));
                        return Mono.empty();
                    })

                    .doFinally(s -> running.set(false))

                    .then();
        });
    }

    private UnifiedMonitoringHostDTO toHost(CameraDevice device) {

        String hostId = Optional.ofNullable(device.getIpAddress())
                .filter(ip -> !ip.isBlank())
                .orElse("UNKNOWN");

        return UnifiedMonitoringHostDTO.builder()
                .id(MonitoringSourceType.CAMERA + ":" + hostId)
                .source(MonitoringSourceType.CAMERA)
                .hostId(hostId)
                .name("Camera-" + hostId)
                .ip(device.getIpAddress())
                .port(device.getPort())
                .protocol(resolveProtocol(device.getPort()))
                .status(Optional.ofNullable(device.getStatus()).map(Enum::name).orElse("UNKNOWN"))
                .category("CAMERA")
                .lastCheck(device.getLastCheckedAt() != null
                        ? java.time.LocalDateTime.ofInstant(device.getLastCheckedAt(), java.time.ZoneId.systemDefault())
                        : device.getLastSeen() != null
                        ? java.time.LocalDateTime.ofInstant(device.getLastSeen(), java.time.ZoneId.systemDefault())
                        : null)
                .build();
    }

    private String resolveProtocol(Integer port) {
        return switch (port == null ? -1 : port) {
            case 554 -> "RTSP";
            case 80, 8080 -> "HTTP";
            default -> "TCP";
        };
    }

    private void safeSaveSnapshot(List<UnifiedMonitoringHostDTO> hosts) {
        try {
            snapshotStore.save(
                    DATASET_HOSTS,
                    SOURCE,
                    StoredSnapshot.of(hosts, false,
                            Map.of(SOURCE, StoredSnapshot.FRESHNESS_LIVE))
            );
        } catch (Exception e) {
            log.warn("Snapshot save failed: {}", safeMessage(e));
        }
    }

    private void subscribeSafely(String op, Mono<Void> mono) {
        mono.subscribe(
                v -> {},
                e -> log.warn("Camera {} failed: {}", op, safeMessage(e))
        );
    }

    private String safeMessage(Throwable t) {
        return (t != null && t.getMessage() != null && !t.getMessage().isBlank())
                ? t.getMessage()
                : (t != null ? t.getClass().getSimpleName() : "unknown");
    }
}
