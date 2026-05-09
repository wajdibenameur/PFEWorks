package tn.iteam.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import org.springframework.test.util.ReflectionTestUtils;
import tn.iteam.adapter.camera.CameraAdapter;
import tn.iteam.adapter.observium.ObserviumAdapter;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.adapter.zkbio.ZkBioAdapter;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ObserviumMetricDTO;
import tn.iteam.dto.ObserviumProblemDTO;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.dto.ZkBioAttendanceDTO;
import tn.iteam.dto.ZkBioMetricDTO;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.mapper.ObserviumMonitoringMapper;
import tn.iteam.mapper.ZabbixMonitoringMapper;
import tn.iteam.mapper.ZkBioMonitoringMapper;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.snapshot.InMemorySnapshotStore;
import tn.iteam.repository.ObserviumMetricRepository;
import tn.iteam.repository.ObserviumProblemRepository;
import tn.iteam.repository.ZkBioMetricRepository;
import tn.iteam.repository.ZkBioProblemRepository;
import tn.iteam.service.MonitoredHostPersistenceService;
import tn.iteam.service.MonitoredHostSnapshotService;
import tn.iteam.service.ObserviumPersistenceService;
import tn.iteam.service.ServiceStatusPersistenceService;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.ZabbixMetricsService;
import tn.iteam.service.ZabbixProblemService;
import tn.iteam.service.ZabbixSyncService;
import tn.iteam.service.ZkBioPersistenceService;
import tn.iteam.service.ZkBioServiceInterface;
import tn.iteam.websocket.MonitoringWebSocketPublisher;
import tn.iteam.websocket.ZkBioWebSocketPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationServicesWithoutRedisTest {

    @Mock
    private ZabbixAdapter zabbixAdapter;

    @Mock
    private ServiceStatusPersistenceService serviceStatusPersistenceService;

    @Mock
    private ZabbixProblemService zabbixProblemService;

    @Mock
    private ZabbixMetricsService zabbixMetricsService;

    @Mock
    private SourceAvailabilityService sourceAvailabilityService;

    @Mock
    private ObserviumAdapter observiumAdapter;

    @Mock
    private ObserviumPersistenceService observiumPersistenceService;

    @Mock
    private ZkBioServiceInterface zkBioService;

    @Mock
    private ZkBioAdapter zkBioAdapter;

    @Mock
    private ZkBioPersistenceService zkBioPersistenceService;

    @Mock
    private MonitoringWebSocketPublisher monitoringWebSocketPublisher;

    @Mock
    private ZkBioWebSocketPublisher zkBioWebSocketPublisher;

    @Mock
    private CameraAdapter cameraAdapter;

    @Mock
    private MonitoredHostPersistenceService monitoredHostPersistenceService;

    @Mock
    private MonitoredHostSnapshotService monitoredHostSnapshotService;

    @Mock
    private ZabbixSyncService zabbixSyncService;

    @Mock
    private ObserviumProblemRepository observiumProblemRepository;

    @Mock
    private ObserviumMetricRepository observiumMetricRepository;

    @Mock
    private ZkBioProblemRepository zkBioProblemRepository;

    @Mock
    private ZkBioMetricRepository zkBioMetricRepository;

    @Test
    void zabbixIntegrationRefreshStoresAllSnapshotsInMemory() {
        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        ZabbixIntegrationService service = new ZabbixIntegrationService(
                zabbixAdapter,
                new ZabbixMonitoringMapper(),
                serviceStatusPersistenceService,
                zabbixProblemService,
                zabbixMetricsService,
                snapshotStore,
                sourceAvailabilityService,
                monitoredHostSnapshotService,
                zabbixSyncService
        );

        when(zabbixAdapter.fetchAll()).thenReturn(List.of(serviceStatus("zbx-host", "10.0.0.1")));
        when(zabbixProblemService.synchronizeActiveProblemsFromZabbix()).thenReturn(List.of(zabbixProblem("p1")));
        when(zabbixMetricsService.fetchAndSaveMetrics()).thenReturn(Mono.just(List.of(zabbixMetric("1001"))));
        when(monitoredHostSnapshotService.loadHosts(MonitoringSourceType.ZABBIX))
                .thenReturn(List.of(unifiedHost("ZABBIX", "zbx-1", "zbx-host", "10.0.0.1", 8080)));

        service.refreshAsync().block();

        assertThat(snapshotStore.get("hosts", "ZABBIX")).isPresent();
        assertThat(snapshotStore.get("problems", "ZABBIX")).isPresent();
        assertThat(snapshotStore.get("metrics", "ZABBIX")).isPresent();
        verify(sourceAvailabilityService, times(3)).markAvailable(MonitoringSourceType.ZABBIX.name());
    }

    @Test
    void observiumIntegrationRefreshStoresAllSnapshotsInMemory() {
        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        ObserviumIntegrationService service = new ObserviumIntegrationService(
                observiumAdapter,
                new ObserviumMonitoringMapper(),
                observiumPersistenceService,
                serviceStatusPersistenceService,
                snapshotStore,
                sourceAvailabilityService,
                monitoredHostPersistenceService,
                monitoredHostSnapshotService,
                observiumProblemRepository,
                observiumMetricRepository
        );

        when(observiumAdapter.fetchAll()).thenReturn(List.of(serviceStatus("obs-host", "10.0.0.2")));
        when(observiumAdapter.fetchProblems()).thenReturn(List.of(observiumProblem("obs-p1")));
        when(observiumAdapter.fetchMetrics()).thenReturn(List.of(observiumMetric("obs-m1")));
        when(monitoredHostSnapshotService.loadHosts(MonitoringSourceType.OBSERVIUM))
                .thenReturn(List.of(unifiedHost("OBSERVIUM", "10.0.0.2", "obs-host", "10.0.0.2", 8080)));

        service.refreshAsync().block();

        assertThat(snapshotStore.get("hosts", "OBSERVIUM")).isPresent();
        assertThat(snapshotStore.get("problems", "OBSERVIUM")).isPresent();
        assertThat(snapshotStore.get("metrics", "OBSERVIUM")).isPresent();
        verify(sourceAvailabilityService, times(3)).markAvailable(MonitoringSourceType.OBSERVIUM.name());
    }

    @Test
    void zkbioIntegrationRefreshAllAndPublishWorksWithoutRedis() {
        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        ZkBioIntegrationService service = new ZkBioIntegrationService(
                zkBioService,
                zkBioAdapter,
                new ZkBioMonitoringMapper(),
                serviceStatusPersistenceService,
                zkBioPersistenceService,
                snapshotStore,
                sourceAvailabilityService,
                monitoringWebSocketPublisher,
                zkBioWebSocketPublisher,
                monitoredHostPersistenceService,
                monitoredHostSnapshotService,
                zkBioProblemRepository,
                zkBioMetricRepository
        );

        when(zkBioAdapter.fetchAll()).thenReturn(List.of(serviceStatus("zk-host", "10.0.0.3")));
        when(zkBioAdapter.fetchProblems()).thenReturn(List.of(zkBioProblem("zk-p1")));
        when(zkBioAdapter.fetchMetrics()).thenReturn(List.of(zkBioMetric("zk-m1")));
        when(zkBioService.getServerStatus()).thenReturn(serviceStatus("zk-server", "10.0.0.30"));
        when(zkBioService.fetchDevices()).thenReturn(List.of(serviceStatus("device-1", "10.0.0.31")));
        when(monitoredHostSnapshotService.loadHosts(MonitoringSourceType.ZKBIO))
                .thenReturn(List.of(unifiedHost("ZKBIO", "10.0.0.3", "zk-host", "10.0.0.3", 8088)));
        when(zkBioService.fetchAttendanceLogs()).thenReturn(List.of(
                ZkBioAttendanceDTO.builder()
                        .userId("u1")
                        .userName("Alice")
                        .deviceId("d1")
                        .timestamp(123L)
                        .build()
        ));

        service.refreshAllAndPublishAsync().block();

        assertThat(snapshotStore.get("hosts", "ZKBIO")).isPresent();
        assertThat(snapshotStore.get("problems", "ZKBIO")).isPresent();
        assertThat(snapshotStore.get("metrics", "ZKBIO")).isPresent();
        assertThat(snapshotStore.get("status", "ZKBIO")).isPresent();
        assertThat(snapshotStore.get("devices", "ZKBIO")).isPresent();
        assertThat(snapshotStore.get("attendance", "ZKBIO")).isPresent();
        verify(monitoringWebSocketPublisher).publishProblemsFromSnapshot(MonitoringSourceType.ZKBIO);
        verify(monitoringWebSocketPublisher).publishMetricsFromSnapshot(MonitoringSourceType.ZKBIO);
        verify(zkBioWebSocketPublisher).publishAttendanceFromSnapshot();
        verify(zkBioWebSocketPublisher).publishDevicesFromSnapshot();
        verify(zkBioWebSocketPublisher).publishStatusFromSnapshot();
    }

    @Test
    void cameraIntegrationRefreshStoresHostsInMemory() {
        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        CameraIntegrationService service = new CameraIntegrationService(
                cameraAdapter,
                serviceStatusPersistenceService,
                snapshotStore
        );
        ReflectionTestUtils.setField(service, "cameraSubnet", "192.168.11");
        ReflectionTestUtils.setField(service, "cameraPorts", "37777,554");

        when(cameraAdapter.fetchAll(List.of("192.168.11"), List.of(37777, 554))).thenReturn(List.of(serviceStatus("camera-1", "10.0.0.40")));

        service.refreshAsync().block();

        assertThat(snapshotStore.get("hosts", "CAMERA")).isPresent();
        verify(serviceStatusPersistenceService).saveAll(any());
    }

    private ServiceStatusDTO serviceStatus(String name, String ip) {
        return ServiceStatusDTO.builder()
                .name(name)
                .ip(ip)
                .status("UP")
                .category("SERVER")
                .protocol("HTTP")
                .port(8080)
                .build();
    }

    private ZabbixProblemDTO zabbixProblem(String problemId) {
        return ZabbixProblemDTO.builder()
                .problemId(problemId)
                .host("zbx-host")
                .hostId("zbx-1")
                .description("CPU alert")
                .severity("HIGH")
                .active(true)
                .eventId(1L)
                .startedAt(100L)
                .status("ACTIVE")
                .build();
    }

    private ZabbixMetric zabbixMetric(String itemId) {
        return ZabbixMetric.builder()
                .hostId("zbx-1")
                .hostName("zbx-host")
                .itemId(itemId)
                .metricKey("system.cpu.util")
                .value(42.0)
                .timestamp(200L)
                .ip("10.0.0.1")
                .port(10051)
                .build();
    }

    private ObserviumProblemDTO observiumProblem(String problemId) {
        return ObserviumProblemDTO.builder()
                .problemId(problemId)
                .host("obs-host")
                .hostId("obs-1")
                .description("Observium alert")
                .severity("MEDIUM")
                .active(true)
                .eventId(2L)
                .startedAt(300L)
                .build();
    }

    private ObserviumMetricDTO observiumMetric(String itemId) {
        return ObserviumMetricDTO.builder()
                .hostId("obs-1")
                .hostName("obs-host")
                .itemId(itemId)
                .metricKey("memory.used")
                .value(64.0)
                .timestamp(400L)
                .ip("10.0.0.2")
                .port(80)
                .build();
    }

    private ZkBioProblemDTO zkBioProblem(String problemId) {
        return ZkBioProblemDTO.builder()
                .problemId(problemId)
                .host("zk-host")
                .description("Door offline")
                .severity("HIGH")
                .active(true)
                .status("ACTIVE")
                .eventId(3L)
                .startedAt(500L)
                .build();
    }

    private ZkBioMetricDTO zkBioMetric(String itemId) {
        return ZkBioMetricDTO.builder()
                .hostId("zk-1")
                .hostName("zk-host")
                .itemId(itemId)
                .metricKey("attendance.latency")
                .value(12.0)
                .timestamp(600L)
                .ip("10.0.0.3")
                .port(8088)
                .build();
    }

    private UnifiedMonitoringHostDTO unifiedHost(
            String source,
            String hostId,
            String name,
            String ip,
            Integer port
    ) {
        return UnifiedMonitoringHostDTO.builder()
                .id(source + ":" + hostId)
                .source(MonitoringSourceType.valueOf(source))
                .hostId(hostId)
                .name(name)
                .ip(ip)
                .port(port)
                .protocol("HTTP")
                .status("UP")
                .category("SERVER")
                .build();
    }
}
