package tn.iteam;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.ResponseEntity;
import tn.iteam.controller.MonitoringController;
import tn.iteam.domain.ApiResponse;
import tn.iteam.integration.CameraIntegrationService;
import tn.iteam.integration.IntegrationService;
import tn.iteam.integration.ObserviumIntegrationService;
import tn.iteam.integration.ZabbixIntegrationService;
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
import tn.iteam.websocket.MonitoringWebSocketPublisher;
import tn.iteam.websocket.ZkBioWebSocketPublisher;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class MonitoringRuntimeIsolationTest {

    @Mock
    private IntegrationService zabbixIntegrationService;

    @Mock
    private IntegrationService observiumIntegrationService;

    @Mock
    private IntegrationService zkBioIntegrationService;

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
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void startupWarmupSwallowsFailuresAndNeverTurnsIntoFatalError() {
        MonitoringStartup startup = new MonitoringStartup(
                zabbixIntegrationService,
                observiumIntegrationService,
                zkBioIntegrationService,
                cameraIntegrationService,
                monitoringWebSocketPublisher,
                zkBioWebSocketPublisher
        );

        doThrow(new RuntimeException("redis-down-or-anything-else")).when(zabbixIntegrationService).refresh();
        doThrow(new RuntimeException("observium-failure")).when(observiumIntegrationService).refresh();
        doThrow(new RuntimeException("zkbio-failure")).when(zkBioIntegrationService).refresh();
        doThrow(new RuntimeException("camera-failure")).when(cameraIntegrationService).refresh();

        assertThatCode(startup::warmupInitialSnapshots).doesNotThrowAnyException();
    }

    @Test
    void schedulersOnlyDependOnIntegrationServicesAndPublishers() {
        ZabbixScheduler zabbixScheduler = new ZabbixScheduler(concreteZabbixIntegrationService, monitoringWebSocketPublisher);
        ObserviumScheduler observiumScheduler = new ObserviumScheduler(concreteObserviumIntegrationService, monitoringWebSocketPublisher);
        ObserviumHostsScheduler observiumHostsScheduler = new ObserviumHostsScheduler(concreteObserviumIntegrationService);
        ZkBioScheduler zkBioScheduler = new ZkBioScheduler(zkBioIntegrationService, monitoringWebSocketPublisher, zkBioWebSocketPublisher);

        zabbixScheduler.fetchAndPublishProblems();
        zabbixScheduler.fetchAndPublishMetrics();
        observiumScheduler.refreshProblemsAndMetrics();
        observiumHostsScheduler.refreshHosts();
        zkBioScheduler.refreshProblemsAndMetrics();
        zkBioScheduler.refreshAttendanceDevicesAndStatus();

        verify(concreteZabbixIntegrationService).refreshProblems();
        verify(concreteZabbixIntegrationService).refreshMetrics();
        verify(concreteObserviumIntegrationService).refreshProblems();
        verify(concreteObserviumIntegrationService).refreshMetrics();
        verify(concreteObserviumIntegrationService).refreshHosts();
        verify(zkBioIntegrationService).refreshProblems();
        verify(zkBioIntegrationService).refreshMetrics();
        verify(zkBioIntegrationService).refreshAttendance();
        verify(monitoringWebSocketPublisher).publishProblemsFromSnapshot(MonitoringSourceType.ZABBIX);
        verify(monitoringWebSocketPublisher).publishMetricsFromSnapshot(MonitoringSourceType.ZABBIX);
        verify(monitoringWebSocketPublisher).publishProblemsFromSnapshot(MonitoringSourceType.OBSERVIUM);
        verify(monitoringWebSocketPublisher).publishMetricsFromSnapshot(MonitoringSourceType.OBSERVIUM);
        verify(monitoringWebSocketPublisher).publishProblemsFromSnapshot(MonitoringSourceType.ZKBIO);
        verify(monitoringWebSocketPublisher).publishMetricsFromSnapshot(MonitoringSourceType.ZKBIO);
        verify(zkBioWebSocketPublisher).publishAttendanceFromSnapshot();
        verify(zkBioWebSocketPublisher).publishDevicesFromSnapshot();
        verify(zkBioWebSocketPublisher).publishStatusFromSnapshot();
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
                cameraIntegrationService,
                monitoringAggregationService,
                sourceAvailabilityService,
                concreteZabbixIntegrationService,
                concreteObserviumIntegrationService,
                zkBioIntegrationService,
                monitoringWebSocketPublisher,
                zkBioWebSocketPublisher
        );

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
        verify(monitoringWebSocketPublisher).publishProblemsFromSnapshot(MonitoringSourceType.ZABBIX);
        verify(monitoringWebSocketPublisher).publishMetricsFromSnapshot(MonitoringSourceType.ZABBIX);
        verify(monitoringWebSocketPublisher).publishProblemsFromSnapshot(MonitoringSourceType.OBSERVIUM);
        verify(monitoringWebSocketPublisher).publishMetricsFromSnapshot(MonitoringSourceType.OBSERVIUM);
        verify(monitoringWebSocketPublisher).publishProblemsFromSnapshot(MonitoringSourceType.ZKBIO);
        verify(monitoringWebSocketPublisher).publishMetricsFromSnapshot(MonitoringSourceType.ZKBIO);
        verify(zkBioWebSocketPublisher).publishAttendanceFromSnapshot();
        verify(zkBioWebSocketPublisher).publishDevicesFromSnapshot();
        verify(zkBioWebSocketPublisher).publishStatusFromSnapshot();
    }
}
