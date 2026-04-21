package tn.iteam.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.iteam.adapter.camera.CameraAdapter;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.snapshot.SnapshotStore;
import tn.iteam.monitoring.snapshot.StoredSnapshot;
import tn.iteam.service.ServiceStatusPersistenceService;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CameraIntegrationService implements IntegrationService {

    private static final String DATASET_HOSTS = "hosts";
    private static final String FRESHNESS_LIVE = "live";

    private final CameraAdapter cameraAdapter;
    private final ServiceStatusPersistenceService serviceStatusPersistenceService;
    private final SnapshotStore snapshotStore;

    @Value("${camera.subnet:192.168.11}")
    private String cameraSubnet;

    @Override
    public MonitoringSourceType getSourceType() {
        return MonitoringSourceType.CAMERA;
    }

    @Override
    public void refresh() {
        refreshHosts();
    }

    @Override
    public void refreshHosts() {
        List<ServiceStatusDTO> statuses = List.copyOf(cameraAdapter.fetchAll(cameraSubnet));
        serviceStatusPersistenceService.saveAll(statuses);

        List<UnifiedMonitoringHostDTO> hosts = statuses.stream()
                .map(this::toHost)
                .toList();

        snapshotStore.save(
                DATASET_HOSTS,
                getSourceType().name(),
                StoredSnapshot.of(hosts, false, Map.of(getSourceType().name(), FRESHNESS_LIVE))
        );

        log.debug("Stored {} camera host snapshot entries", hosts.size());
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
}
