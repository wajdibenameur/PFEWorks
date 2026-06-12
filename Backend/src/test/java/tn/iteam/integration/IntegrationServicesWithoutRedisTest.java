package tn.iteam.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import org.springframework.test.util.ReflectionTestUtils;
import tn.iteam.adapter.camera.CameraAdapter;
import tn.iteam.adapter.snmp.SnmpAdapter;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.adapter.zkbio.ZkBioAdapter;
import tn.iteam.domain.CameraDevice;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.SnmpMetricDTO;
import tn.iteam.dto.SnmpProblemDTO;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.dto.ZkBioAttendanceDTO;
import tn.iteam.dto.ZkBioMetricDTO;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.mapper.SnmpMonitoringMapper;
import tn.iteam.mapper.ZabbixMonitoringMapper;
import tn.iteam.mapper.ZkBioMonitoringMapper;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.snapshot.InMemorySnapshotStore;
import tn.iteam.repository.SnmpMetricRepository;
import tn.iteam.repository.SnmpProblemRepository;
import tn.iteam.repository.ZkBioMetricRepository;
import tn.iteam.repository.ZkBioProblemRepository;
import tn.iteam.service.MonitoredHostPersistenceService;
import tn.iteam.service.MonitoredHostSnapshotService;
import tn.iteam.service.SnmpPersistenceService;
import tn.iteam.service.ServiceStatusPersistenceService;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.ZabbixMetricsService;
import tn.iteam.service.ZabbixProblemService;
import tn.iteam.service.ZabbixHostSyncService;
import tn.iteam.service.ZkBioPersistenceService;
import tn.iteam.service.ZkBioServiceInterface;
import tn.iteam.service.camera.CameraHealthPollingService;
import tn.iteam.service.support.DatabasePersistenceGuard;
import tn.iteam.service.support.MonitoringFreshnessService;
import tn.iteam.service.support.MonitoringSnapshotPublicationService;
import tn.iteam.websocket.MonitoringWebSocketPublisher;
import tn.iteam.websocket.ZkBioWebSocketPublisher;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
    private SnmpAdapter snmpAdapter;

    @Mock
    private SnmpPersistenceService snmpPersistenceService;

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
    private MonitoringSnapshotPublicationService monitoringSnapshotPublicationService;

    @Mock
    private CameraAdapter cameraAdapter;

    @Mock
    private CameraHealthPollingService cameraHealthPollingService;

    @Mock
    private MonitoredHostPersistenceService monitoredHostPersistenceService;

    @Mock
    private MonitoredHostSnapshotService monitoredHostSnapshotService;

    @Mock
    private ZabbixHostSyncService zabbixSyncService;

    @Mock
    private SnmpProblemRepository snmpProblemRepository;

    @Mock
    private SnmpMetricRepository snmpMetricRepository;

    @Mock
    private ZkBioProblemRepository zkBioProblemRepository;

    @Mock
    private ZkBioMetricRepository zkBioMetricRepository;

    @Mock
    private MonitoringFreshnessService monitoringFreshnessService;

    @Mock
    private DatabasePersistenceGuard databasePersistenceGuard;

    @BeforeEach
    void setupFreshnessDefaults() {
        when(monitoringFreshnessService.shouldSkipFetch(anyString(), anyString(), anyLong())).thenReturn(false);
        when(monitoringFreshnessService.hasPersistDelta(anyString(), anyString(), any())).thenReturn(true);
        when(monitoringFreshnessService.hasPublishDelta(anyString(), anyString(), any())).thenReturn(true);
        when(databasePersistenceGuard.safeRun(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(2);
            runnable.run();
            return true;
        });
        when(databasePersistenceGuard.safeLoad(anyString(), anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(2);
            return supplier.get();
        });
    }

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
                zabbixSyncService,
                monitoringFreshnessService,
                databasePersistenceGuard
        );

        when(zabbixAdapter.fetchAll()).thenReturn(List.of(serviceStatus("zbx-host", "10.0.0.1")));
        when(zabbixProblemService.synchronizeActiveProblemsFromZabbix()).thenReturn(List.of(zabbixProblem("p1")));
        when(zabbixMetricsService.fetchAndSaveMetrics()).thenReturn(Mono.just(List.of(zabbixMetric("1001"))));
        when(monitoredHostSnapshotService.loadHosts(MonitoringSourceType.ZABBIX))
                .thenReturn(List.of(unifiedHost("ZABBIX", "zbx-1", "zbx-host", "10.0.0.1", 8080)));

        service.refreshAsync().block();

        verify(zabbixAdapter, atLeastOnce()).fetchHosts();
    }

    @Test
    void snmpIntegrationRefreshStoresAllSnapshotsInMemory() {
        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        SnmpIntegrationService service = new SnmpIntegrationService(
                snmpAdapter,
                new SnmpMonitoringMapper(),
                snmpPersistenceService,
                serviceStatusPersistenceService,
                snapshotStore,
                sourceAvailabilityService,
                monitoredHostPersistenceService,
                monitoredHostSnapshotService,
                snmpProblemRepository,
                snmpMetricRepository,
                monitoringFreshnessService,
                databasePersistenceGuard
        );

        when(snmpAdapter.fetchAll()).thenReturn(List.of(serviceStatus("obs-host", "10.0.0.2")));
        when(snmpAdapter.fetchProblems()).thenReturn(List.of(snmpProblem("obs-p1")));
        when(snmpAdapter.fetchMetrics()).thenReturn(List.of(snmpMetric("obs-m1")));
        when(monitoredHostSnapshotService.loadHosts(MonitoringSourceType.SNMP))
                .thenReturn(List.of(unifiedHost("SNMP", "10.0.0.2", "obs-host", "10.0.0.2", 8080)));

        service.refreshAsync().block();

        assertThat(snapshotStore.get("hosts", "SNMP")).isPresent();
        assertThat(snapshotStore.get("problems", "SNMP")).isPresent();
        assertThat(snapshotStore.get("metrics", "SNMP")).isPresent();
        @SuppressWarnings("unchecked")
        List<UnifiedMonitoringHostDTO> hosts = (List<UnifiedMonitoringHostDTO>) snapshotStore.get("hosts", "SNMP").orElseThrow().data();
        assertThat(hosts).hasSize(1);
        assertThat(hosts.get(0).getLastCheck()).isEqualTo(LocalDateTime.of(2026, 6, 3, 11, 0));
        verify(sourceAvailabilityService, times(3)).markAvailable(MonitoringSourceType.SNMP.name());
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
                monitoringSnapshotPublicationService,
                zkBioWebSocketPublisher,
                monitoredHostPersistenceService,
                monitoredHostSnapshotService,
                zkBioProblemRepository,
                zkBioMetricRepository,
                monitoringFreshnessService,
                databasePersistenceGuard
        );

        when(zkBioAdapter.fetchAll()).thenReturn(List.of(serviceStatus("zk-host", "10.0.0.3")));
        when(zkBioAdapter.fetchProblems()).thenReturn(List.of(zkBioProblem("zk-p1")));
        when(zkBioAdapter.fetchMetrics()).thenReturn(List.of(zkBioMetric("zk-m1")));
        when(zkBioService.getServerStatus()).thenReturn(serviceStatus("zk-server", "10.0.0.30"));
        when(zkBioService.fetchDevices()).thenReturn(List.of(serviceStatus("device-1", "10.0.0.31")));
        when(monitoredHostSnapshotService.loadHosts(MonitoringSourceType.ZKBIO))
                .thenReturn(List.of(unifiedHost("ZKBIO", "10.0.0.3", "zk-host", "10.0.0.3", 8088)));
        when(zkBioService.fetchAttendanceLogs(anyLong(), anyLong())).thenReturn(List.of(
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
        verify(monitoringSnapshotPublicationService).publishProblemsSnapshot(MonitoringSourceType.ZKBIO);
        verify(monitoringSnapshotPublicationService).publishMetricsSnapshot(MonitoringSourceType.ZKBIO);
        verify(zkBioWebSocketPublisher).publishAttendanceFromSnapshot();
        verify(zkBioWebSocketPublisher).publishDevicesFromSnapshot();
        verify(zkBioWebSocketPublisher).publishStatusFromSnapshot();
    }

    @Test
    void cameraIntegrationRefreshStoresHostsInMemory() {
        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        CameraIntegrationService service = new CameraIntegrationService(
                cameraHealthPollingService,
                snapshotStore
        );
        when(cameraHealthPollingService.pollNow()).thenReturn(
                new CameraHealthPollingService.PollingResult(
                        List.of(
                                CameraDevice.builder()
                                        .ipAddress("10.0.0.40")
                                        .port(8080)
                                        .status(tn.iteam.enums.DeviceStatus.UP)
                                        .lastCheckedAt(Instant.parse("2026-06-03T09:15:00Z"))
                                        .enabled(true)
                                        .build()
                        ),
                        List.of()
                )
        );

        service.refreshAsync().block();

        assertThat(snapshotStore.get("hosts", "CAMERA")).isPresent();
        @SuppressWarnings("unchecked")
        List<UnifiedMonitoringHostDTO> hosts = (List<UnifiedMonitoringHostDTO>) snapshotStore.get("hosts", "CAMERA").orElseThrow().data();
        assertThat(hosts).hasSize(1);
        assertThat(hosts.get(0).getLastCheck()).isNotNull();
        verify(cameraHealthPollingService).pollNow();
    }

    private ServiceStatusDTO serviceStatus(String name, String ip) {
        return ServiceStatusDTO.builder()
                .name(name)
                .ip(ip)
                .status("UP")
                .category("SERVER")
                .protocol("HTTP")
                .port(8080)
                .lastCheck(LocalDateTime.of(2026, 6, 3, 11, 0))
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

    private SnmpProblemDTO snmpProblem(String problemId) {
        return SnmpProblemDTO.builder()
                .problemId(problemId)
                .host("obs-host")
                .hostId("obs-1")
                .description("SNMP alert")
                .severity("MEDIUM")
                .active(true)
                .eventId(2L)
                .startedAt(300L)
                .lastObservedAt(360L)
                .build();
    }

    private SnmpMetricDTO snmpMetric(String itemId) {
        return SnmpMetricDTO.builder()
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
