package tn.iteam;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import tn.iteam.controller.MonitoringController;
import tn.iteam.domain.ApiResponse;
import tn.iteam.integration.IntegrationServiceRegistry;
import tn.iteam.integration.CameraIntegrationService;
import tn.iteam.integration.ObserviumIntegrationService;
import tn.iteam.integration.ZabbixIntegrationService;
import tn.iteam.integration.ZkBioIntegrationService;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.service.MonitoringAggregationService;
import tn.iteam.monitoring.snapshot.InMemorySnapshotStore;
import tn.iteam.monitoring.snapshot.StoredSnapshot;
import tn.iteam.scheduler.ObserviumHostsScheduler;
import tn.iteam.scheduler.ObserviumScheduler;
import tn.iteam.scheduler.ZabbixScheduler;
import tn.iteam.scheduler.ZkBioScheduler;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.support.MonitoringSnapshotPublicationService;
import tn.iteam.websocket.MonitoringWebSocketPublisher;
import tn.iteam.websocket.ZkBioWebSocketPublisher;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MonitoringRuntimeIsolationTest {

    @Mock
    private ZabbixIntegrationService zabbixIntegrationService;

    @Mock
    private ObserviumIntegrationService observiumIntegrationService;

    @Mock
    private ZkBioIntegrationService zkBioIntegrationService;

    @Mock
    private CameraIntegrationService cameraIntegrationService;

    @Mock
    private MonitoringWebSocketPublisher monitoringWebSocketPublisher;

    @Mock
    private ZkBioWebSocketPublisher zkBioWebSocketPublisher;

    @Mock
    private ZabbixIntegrationService concreteZabbixIntegrationService;

    @Mock
    private ObserviumIntegrationService concreteObserviumIntegrationService;

    @Mock
    private MonitoringAggregationService monitoringAggregationService;

    @Mock
    private SourceAvailabilityService sourceAvailabilityService;

    @Mock
    private IntegrationServiceRegistry integrationServiceRegistry;

    @Mock
    private MonitoringSnapshotPublicationService snapshotPublicationService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void startupWarmupSwallowsFailuresAndNeverTurnsIntoFatalError() {
        MonitoringStartup startup = new MonitoringStartup(
                integrationServiceRegistry,
                zkBioIntegrationService,
                snapshotPublicationService
        );

        when(integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX)).thenReturn(zabbixIntegrationService);
        when(integrationServiceRegistry.getRequired(MonitoringSourceType.OBSERVIUM)).thenReturn(observiumIntegrationService);
        when(integrationServiceRegistry.getRequired(MonitoringSourceType.CAMERA)).thenReturn(cameraIntegrationService);

        when(zabbixIntegrationService.refreshAsync()).thenReturn(Mono.error(new RuntimeException("redis-down-or-anything-else")));
        when(observiumIntegrationService.refreshAsync()).thenReturn(Mono.error(new RuntimeException("observium-failure")));
        when(zkBioIntegrationService.refreshAsync()).thenReturn(Mono.error(new RuntimeException("zkbio-failure")));
        when(zkBioIntegrationService.refreshAttendanceAsync()).thenReturn(Mono.error(new RuntimeException("zkbio-failure")));
        when(cameraIntegrationService.refreshAsync()).thenReturn(Mono.error(new RuntimeException("camera-failure")));

        assertThatCode(startup::warmupInitialSnapshots).doesNotThrowAnyException();
    }

    @Test
    void schedulersOnlyDependOnIntegrationServicesAndPublishers() {
        ZabbixScheduler zabbixScheduler = new ZabbixScheduler(integrationServiceRegistry, snapshotPublicationService);
        ObserviumScheduler observiumScheduler = new ObserviumScheduler(integrationServiceRegistry, snapshotPublicationService);
        ObserviumHostsScheduler observiumHostsScheduler = new ObserviumHostsScheduler(integrationServiceRegistry);
        ZkBioScheduler zkBioScheduler = new ZkBioScheduler(zkBioIntegrationService, snapshotPublicationService);

        when(integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX)).thenReturn(concreteZabbixIntegrationService);
        when(integrationServiceRegistry.getRequired(MonitoringSourceType.OBSERVIUM)).thenReturn(concreteObserviumIntegrationService);

        when(concreteZabbixIntegrationService.refreshMetricsAsync()).thenReturn(Mono.empty());
        when(concreteObserviumIntegrationService.refreshAsync()).thenReturn(Mono.empty());
        when(zkBioIntegrationService.refreshAsync()).thenReturn(Mono.empty());
        when(zkBioIntegrationService.refreshAttendanceAsync()).thenReturn(Mono.empty());

        zabbixScheduler.fetchAndPublishProblems();
        zabbixScheduler.fetchAndPublishMetrics();
        observiumScheduler.refreshProblemsAndMetrics();
        observiumHostsScheduler.refreshHosts();
        zkBioScheduler.refreshProblemsAndMetrics();
        zkBioScheduler.refreshAttendanceDevicesAndStatus();

        verify(concreteZabbixIntegrationService).refreshProblems();
        verify(concreteZabbixIntegrationService).refreshMetricsAsync();
        verify(concreteObserviumIntegrationService).refreshAsync();
        verify(concreteObserviumIntegrationService).refreshHosts();
        verify(zkBioIntegrationService).refreshAsync();
        verify(zkBioIntegrationService).refreshAttendanceAsync();
        verify(snapshotPublicationService).publishProblemsSnapshot(MonitoringSourceType.ZABBIX);
        verify(snapshotPublicationService).publishMetricsSnapshot(MonitoringSourceType.ZABBIX);
        verify(snapshotPublicationService).publishMonitoringSnapshots(MonitoringSourceType.OBSERVIUM);
        verify(snapshotPublicationService).publishMonitoringSnapshots(MonitoringSourceType.ZKBIO);
        verify(snapshotPublicationService).publishZkBioSnapshots();
    }

    @Test
    void publishersReadFromInMemorySnapshotStoreWithoutRedis() {
        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        MonitoringWebSocketPublisher monitoringPublisher = new MonitoringWebSocketPublisher(messagingTemplate, snapshotStore);
        ZkBioWebSocketPublisher zkBioPublisher = new ZkBioWebSocketPublisher(messagingTemplate, snapshotStore);

        snapshotStore.save(
                "problems",
                "ZABBIX",
                StoredSnapshot.of(
                        List.of(UnifiedMonitoringProblemDTO.builder().id("ZABBIX:p1").problemId("p1").build()),
                        false,
                        Map.of("ZABBIX", "live")
                )
        );
        snapshotStore.save(
                "metrics",
                "ZABBIX",
                StoredSnapshot.of(
                        List.of(UnifiedMonitoringMetricDTO.builder().id("ZABBIX:m1").itemId("m1").build()),
                        false,
                        Map.of("ZABBIX", "live")
                )
        );
        snapshotStore.save(
                "attendance",
                "ZKBIO",
                StoredSnapshot.of(List.of(Map.of("userId", "u1")), false, Map.of("ZKBIO", "live"))
        );
        snapshotStore.save(
                "devices",
                "ZKBIO",
                StoredSnapshot.of(List.of(Map.of("deviceId", "d1")), false, Map.of("ZKBIO", "live"))
        );
        snapshotStore.save(
                "status",
                "ZKBIO",
                StoredSnapshot.of(Map.of("status", "UP"), false, Map.of("ZKBIO", "live"))
        );

        monitoringPublisher.publishProblemsFromSnapshot(MonitoringSourceType.ZABBIX);
        monitoringPublisher.publishMetricsFromSnapshot(MonitoringSourceType.ZABBIX);
        zkBioPublisher.publishAttendanceFromSnapshot();
        zkBioPublisher.publishDevicesFromSnapshot();
        zkBioPublisher.publishStatusFromSnapshot();

        verify(messagingTemplate).convertAndSend(MonitoringWebSocketPublisher.TOPIC_PROBLEMS, List.of(
                UnifiedMonitoringProblemDTO.builder().id("ZABBIX:p1").problemId("p1").build()
        ));
        verify(messagingTemplate).convertAndSend(MonitoringWebSocketPublisher.TOPIC_METRICS, List.of(
                UnifiedMonitoringMetricDTO.builder().id("ZABBIX:m1").itemId("m1").build()
        ));
        verify(messagingTemplate).convertAndSend(ZkBioWebSocketPublisher.TOPIC_ATTENDANCE, List.of(Map.of("userId", "u1")));
        verify(messagingTemplate).convertAndSend(ZkBioWebSocketPublisher.TOPIC_DEVICES, List.of(Map.of("deviceId", "d1")));
        verify(messagingTemplate).convertAndSend(ZkBioWebSocketPublisher.TOPIC_STATUS, Map.of("status", "UP"));
    }

    @Test
    void controllerCollectAllReturnsSuccessWithoutAnyRedisDependency() {
        MonitoringController controller = new MonitoringController(
                monitoringAggregationService,
                sourceAvailabilityService,
                integrationServiceRegistry,
                zkBioIntegrationService,
                snapshotPublicationService
        );

        when(integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX)).thenReturn(concreteZabbixIntegrationService);
        when(integrationServiceRegistry.getRequired(MonitoringSourceType.OBSERVIUM)).thenReturn(concreteObserviumIntegrationService);
        when(integrationServiceRegistry.getRequired(MonitoringSourceType.ZKBIO)).thenReturn(zkBioIntegrationService);
        when(integrationServiceRegistry.getRequired(MonitoringSourceType.CAMERA)).thenReturn(cameraIntegrationService);

        ResponseEntity<ApiResponse<Void>> response = controller.collectAll();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("ALL SERVICES COLLECTED");
        verify(concreteZabbixIntegrationService).refresh();
        verify(concreteObserviumIntegrationService).refresh();
        verify(zkBioIntegrationService).refresh();
        verify(zkBioIntegrationService).refreshAttendance();
        verify(cameraIntegrationService).refresh();
        verify(snapshotPublicationService).publishMonitoringSnapshots(List.of(
                MonitoringSourceType.ZABBIX,
                MonitoringSourceType.OBSERVIUM,
                MonitoringSourceType.ZKBIO
        ));
        verify(snapshotPublicationService).publishZkBioSnapshots();
    }
}
