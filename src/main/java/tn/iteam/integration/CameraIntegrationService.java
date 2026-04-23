package tn.iteam.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tn.iteam.adapter.camera.CameraAdapter;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.snapshot.SnapshotStore;
import tn.iteam.monitoring.snapshot.StoredSnapshot;
import tn.iteam.service.ServiceStatusPersistenceService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraIntegrationService implements AsyncIntegrationService {

    private static final String DATASET_HOSTS = "hosts";
    private static final String FRESHNESS_LIVE = "live";
    private static final String FRESHNESS_SNAPSHOT = "snapshot_fallback";

    private final CameraAdapter cameraAdapter;
    private final ServiceStatusPersistenceService serviceStatusPersistenceService;
    private final SnapshotStore snapshotStore;

    @Value("${camera.subnet:192.168.40,192.168.41}")
    private String cameraSubnet;

    @Value("${camera.ports:37777,554}")
    private String cameraPorts;

    @Override
    public MonitoringSourceType getSourceType() {
        return MonitoringSourceType.CAMERA;
    }

    @Override
    public void refresh() {
        subscribeSafely("refresh", refreshAsync());
    }

    @Override
    public void refreshHosts() {
        subscribeSafely("refreshHosts", refreshAsync());
    }

    public Mono<Void> refreshAsync() {
        String source = getSourceType().name();
        return Mono.fromCallable(() -> {
                    List<ServiceStatusDTO> statuses = List.copyOf(cameraAdapter.fetchAll(parseSubnets(), parsePorts()));
                    serviceStatusPersistenceService.saveAll(statuses);
                    return statuses.stream().map(this::toHost).toList();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(hosts -> saveSnapshot(source, hosts))
                .onErrorResume(throwable -> {
                    handleRefreshFailure(source, toException(throwable));
                    return Mono.empty();
                })
                .then();
    }

    private List<Integer> parsePorts() {
        return java.util.Arrays.stream(cameraPorts.split("\\s*,\\s*"))
                .filter(token -> !token.isBlank())
                .map(token -> {
                    try {
                        return Integer.parseInt(token.trim());
                    } catch (NumberFormatException exception) {
                        log.warn("Ignoring invalid camera port '{}'", token);
                        return null;
                    }
                })
                .filter(port -> port != null && port > 0)
                .collect(Collectors.toList());
    }

    private List<String> parseSubnets() {
        return java.util.Arrays.stream(cameraSubnet.split("\\s*,\\s*"))
                .filter(token -> !token.isBlank())
                .map(String::trim)
                .toList();
    }

    private UnifiedMonitoringHostDTO toHost(ServiceStatusDTO dto) {
        String hostId = dto.getIp() != null && !dto.getIp().isBlank()
                ? dto.getIp()
                : (dto.getName() != null && !dto.getName().isBlank() ? dto.getName() : "CAMERA");

        return UnifiedMonitoringHostDTO.builder()
                .id(MonitoringSourceType.CAMERA + ":" + hostId)
                .source(MonitoringSourceType.CAMERA)
                .hostId(hostId)
                .name(dto.getName())
                .ip(dto.getIp())
                .port(dto.getPort())
                .protocol(dto.getProtocol())
                .status(dto.getStatus())
                .category(dto.getCategory())
                .build();
    }

    private void saveSnapshot(String source, List<UnifiedMonitoringHostDTO> hosts) {
        try {
            snapshotStore.save(
                    DATASET_HOSTS,
                    source,
                    StoredSnapshot.of(hosts, false, Map.of(source, FRESHNESS_LIVE))
            );
            log.debug("Stored {} camera host snapshot entries", hosts.size());
        } catch (Exception exception) {
            log.warn("Unable to store camera hosts snapshot: {}", safeMessage(exception));
        }
    }

    private void handleRefreshFailure(String source, Exception exception) {
        List<?> existingSnapshot = safeGetExistingSnapshot(source).orElse(null);
        if (existingSnapshot != null) {
            saveFallbackSnapshot(source, existingSnapshot);
            log.warn("Failed to refresh camera hosts. Keeping last snapshot: {}", safeMessage(exception));
            return;
        }

        saveFallbackSnapshot(source, List.of());
        log.warn("Failed to refresh camera hosts. Serving empty snapshot fallback: {}", safeMessage(exception));
    }

    private Optional<List<?>> safeGetExistingSnapshot(String source) {
        try {
            return snapshotStore.<List<?>>get(DATASET_HOSTS, source).map(StoredSnapshot::data);
        } catch (Exception exception) {
            log.warn("Unable to read existing camera snapshot: {}", safeMessage(exception));
            return Optional.empty();
        }
    }

    private void saveFallbackSnapshot(String source, List<?> data) {
        try {
            snapshotStore.save(
                    DATASET_HOSTS,
                    source,
                    new StoredSnapshot<>(data, true, Map.of(source, FRESHNESS_SNAPSHOT), Instant.now())
            );
        } catch (Exception snapshotException) {
            log.warn("Unable to save fallback camera snapshot: {}", safeMessage(snapshotException));
        }
    }

    private void subscribeSafely(String operation, Mono<Void> pipeline) {
        pipeline.subscribe(
                unused -> {
                },
                throwable -> log.warn("Camera {} async failed but application remains available: {}", operation, safeMessage(throwable))
        );
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() != null && !exception.getMessage().isBlank()
                ? exception.getMessage()
                : "Unknown integration error";
    }

    private String safeMessage(Throwable throwable) {
        return throwable.getMessage() != null && !throwable.getMessage().isBlank()
                ? throwable.getMessage()
                : "Unknown integration error";
    }

    private Exception toException(Throwable throwable) {
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(throwable);
    }
}
