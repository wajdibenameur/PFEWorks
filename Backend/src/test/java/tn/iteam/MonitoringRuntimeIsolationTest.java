package tn.iteam;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import tn.iteam.controller.MonitoringController;
import tn.iteam.domain.ApiResponse;
import tn.iteam.integration.AsyncIntegrationService;
import tn.iteam.integration.IntegrationServiceRegistry;
import tn.iteam.integration.ZkBioIntegrationOperations;
import tn.iteam.integration.ZkBioRefreshOrchestrationService;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.service.MonitoringAggregationService;
import tn.iteam.monitoring.snapshot.InMemorySnapshotStore;
import tn.iteam.monitoring.snapshot.StoredSnapshot;
import tn.iteam.scheduler.SnmpHostsScheduler;
import tn.iteam.scheduler.SnmpScheduler;
import tn.iteam.scheduler.ZabbixScheduler;
import tn.iteam.scheduler.ZkBioScheduler;
import tn.iteam.service.SnmpInterfaceService;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.support.MonitoringFreshnessService;
import tn.iteam.service.support.MonitoringSnapshotPublicationService;
import tn.iteam.websocket.MonitoringWebSocketPublisher;
import tn.iteam.websocket.ZkBioWebSocketPublisher;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MonitoringRuntimeIsolationTest {

    @Mock
    private AsyncIntegrationService zabbixIntegrationService;

    @Mock
    private AsyncIntegrationService snmpIntegrationService;

    @Mock
    private ZkBioIntegrationOperations zkBioIntegrationService;

    @Mock
    private ZkBioRefreshOrchestrationService zkBioRefreshOrchestrationService;

    @Mock
    private AsyncIntegrationService cameraIntegrationService;

    @Mock
    private MonitoringWebSocketPublisher monitoringWebSocketPublisher;

    @Mock
    private ZkBioWebSocketPublisher zkBioWebSocketPublisher;

    @Mock
    private AsyncIntegrationService concreteZabbixIntegrationService;

    @Mock
    private AsyncIntegrationService concreteSnmpIntegrationService;

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

    @Mock
    private MonitoringFreshnessService monitoringFreshnessService;

    @Mock
    private SnmpInterfaceService snmpInterfaceService;

    @Test
    void startupWarmupSwallowsFailuresAndNeverTurnsIntoFatalError() {
        MonitoringStartup startup = new MonitoringStartup(
                integrationServiceRegistry,
                zkBioRefreshOrchestrationService,
                snapshotPublicationService
        );

        when(integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX)).thenReturn(zabbixIntegrationService);
        when(integrationServiceRegistry.getRequired(MonitoringSourceType.SNMP)).thenReturn(snmpIntegrationService);
        when(integrationServiceRegistry.getRequired(MonitoringSourceType.CAMERA)).thenReturn(cameraIntegrationService);

        when(zabbixIntegrationService.refreshAsync()).thenReturn(Mono.error(new RuntimeException("redis-down-or-anything-else")));
        when(snmpIntegrationService.refreshAsync()).thenReturn(Mono.error(new RuntimeException("snmp-failure")));
        when(zkBioRefreshOrchestrationService.refreshMonitoringAndAttendanceAsync())
                .thenReturn(Mono.error(new RuntimeException("zkbio-failure")));
        when(cameraIntegrationService.refreshAsync()).thenReturn(Mono.error(new RuntimeException("camera-failure")));

        assertThatCode(startup::warmupInitialSnapshots).doesNotThrowAnyException();
    }

    @Test
    void schedulersOnlyDependOnIntegrationServicesAndPublishers() {
        ZabbixScheduler zabbixScheduler = new ZabbixScheduler(
                integrationServiceRegistry,
                sourceAvailabilityService,
                snapshotPublicationService
        );
        SnmpScheduler snmpScheduler = new SnmpScheduler(
                integrationServiceRegistry,
                sourceAvailabilityService,
                snapshotPublicationService
        );
        SnmpHostsScheduler snmpHostsScheduler = new SnmpHostsScheduler(
                integrationServiceRegistry,
                sourceAvailabilityService
        );
        ZkBioScheduler zkBioScheduler = new ZkBioScheduler(
                zkBioIntegrationService,
                sourceAvailabilityService,
                snapshotPublicationService
        );

        when(integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX)).thenReturn(concreteZabbixIntegrationService);
        when(integrationServiceRegistry.getRequired(MonitoringSourceType.SNMP)).thenReturn(concreteSnmpIntegrationService);
        when(sourceAvailabilityService.shouldAttempt(MonitoringSourceType.ZABBIX.name(), 60000L)).thenReturn(true);
        when(sourceAvailabilityService.shouldAttempt(MonitoringSourceType.ZABBIX.name(), 120000L)).thenReturn(true);
        when(sourceAvailabilityService.shouldAttempt(MonitoringSourceType.SNMP.name(), 60000L)).thenReturn(true);
        when(sourceAvailabilityService.shouldAttempt(MonitoringSourceType.ZKBIO.name(), 60000L)).thenReturn(true);

        when(concreteZabbixIntegrationService.refreshMetricsAsync()).thenReturn(Mono.empty());
        when(concreteSnmpIntegrationService.refreshAsync()).thenReturn(Mono.empty());
        when(zkBioIntegrationService.refreshAsync()).thenReturn(Mono.empty());
        when(zkBioIntegrationService.refreshAttendanceAsync()).thenReturn(Mono.empty());

        zabbixScheduler.fetchAndPublishProblems();
        zabbixScheduler.fetchAndPublishMetrics();
        snmpScheduler.refreshProblemsAndMetrics();
        snmpHostsScheduler.refreshHosts();
        zkBioScheduler.refreshProblemsAndMetrics();
        zkBioScheduler.refreshAttendanceDevicesAndStatus();

        verify(concreteZabbixIntegrationService).refreshProblems();
        verify(concreteZabbixIntegrationService).refreshMetricsAsync();
        verify(concreteSnmpIntegrationService).refreshAsync();
        verify(concreteSnmpIntegrationService).refreshHosts();
        verify(zkBioIntegrationService).refreshAsync();
        verify(zkBioIntegrationService).refreshAttendanceAsync();
        verify(snapshotPublicationService).publishProblemsSnapshot(MonitoringSourceType.ZABBIX);
        verify(snapshotPublicationService).publishMetricsSnapshot(MonitoringSourceType.ZABBIX);
        verify(snapshotPublicationService).publishMonitoringSnapshots(MonitoringSourceType.SNMP);
        verify(snapshotPublicationService).publishMonitoringSnapshots(MonitoringSourceType.ZKBIO);
        verify(snapshotPublicationService).publishZkBioSnapshots();
    }

    @Test
    void schedulersSkipRefreshDuringRetryCooldown() {
        ZabbixScheduler zabbixScheduler = new ZabbixScheduler(
                integrationServiceRegistry,
                sourceAvailabilityService,
                snapshotPublicationService
        );
        SnmpScheduler snmpScheduler = new SnmpScheduler(
                integrationServiceRegistry,
                sourceAvailabilityService,
                snapshotPublicationService
        );
        SnmpHostsScheduler snmpHostsScheduler = new SnmpHostsScheduler(
                integrationServiceRegistry,
                sourceAvailabilityService
        );
        ZkBioScheduler zkBioScheduler = new ZkBioScheduler(
                zkBioIntegrationService,
                sourceAvailabilityService,
                snapshotPublicationService
        );

        when(sourceAvailabilityService.shouldAttempt(MonitoringSourceType.ZABBIX.name(), 60000L)).thenReturn(false);
        when(sourceAvailabilityService.shouldAttempt(MonitoringSourceType.ZABBIX.name(), 120000L)).thenReturn(false);
        when(sourceAvailabilityService.shouldAttempt(MonitoringSourceType.SNMP.name(), 60000L)).thenReturn(false);
        when(sourceAvailabilityService.shouldAttempt(MonitoringSourceType.ZKBIO.name(), 60000L)).thenReturn(false);

        zabbixScheduler.fetchAndPublishProblems();
        zabbixScheduler.fetchAndPublishMetrics();
        snmpScheduler.refreshProblemsAndMetrics();
        snmpHostsScheduler.refreshHosts();
        zkBioScheduler.refreshProblemsAndMetrics();
        zkBioScheduler.refreshAttendanceDevicesAndStatus();

        verifyNoInteractions(integrationServiceRegistry);
        verifyNoInteractions(zkBioIntegrationService);
        verifyNoInteractions(snapshotPublicationService);
    }

    @Test
    void publishersReadFromInMemorySnapshotStoreWithoutRedis() {
        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        MonitoringWebSocketPublisher monitoringPublisher = new MonitoringWebSocketPublisher(messagingTemplate);
        ZkBioWebSocketPublisher zkBioPublisher = new ZkBioWebSocketPublisher(messagingTemplate, snapshotStore);
        MonitoringSnapshotPublicationService publicationService = new MonitoringSnapshotPublicationService(
                monitoringPublisher,
                zkBioPublisher,
                snapshotStore,
                monitoringFreshnessService
        );
        ReflectionTestUtils.setField(publicationService, "publishOnlyOnChange", true);

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

        when(monitoringFreshnessService.hasPublishDelta("problems", "ZABBIX", List.of(
                UnifiedMonitoringProblemDTO.builder().id("ZABBIX:p1").problemId("p1").build()
        ))).thenReturn(true);
        when(monitoringFreshnessService.hasPublishDelta("metrics", "ZABBIX", List.of(
                UnifiedMonitoringMetricDTO.builder().id("ZABBIX:m1").itemId("m1").build()
        ))).thenReturn(true);

        publicationService.publishProblemsSnapshot(MonitoringSourceType.ZABBIX);
        publicationService.publishMetricsSnapshot(MonitoringSourceType.ZABBIX);
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
                zkBioRefreshOrchestrationService,
                snapshotPublicationService,
                snmpInterfaceService,
                monitoringFreshnessService
        );

        when(integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX)).thenReturn(concreteZabbixIntegrationService);
        when(integrationServiceRegistry.getRequired(MonitoringSourceType.SNMP)).thenReturn(concreteSnmpIntegrationService);
        when(integrationServiceRegistry.getRequired(MonitoringSourceType.CAMERA)).thenReturn(cameraIntegrationService);
        when(concreteZabbixIntegrationService.refreshAsync()).thenReturn(Mono.empty());
        when(concreteSnmpIntegrationService.refreshAsync()).thenReturn(Mono.empty());
        when(zkBioRefreshOrchestrationService.refreshMonitoringAndAttendanceAsync()).thenReturn(Mono.empty());
        when(cameraIntegrationService.refreshAsync()).thenReturn(Mono.empty());

        ResponseEntity<ApiResponse<Void>> response = controller.collectAll().block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("ALL SERVICES COLLECTED");
        verify(concreteZabbixIntegrationService).refreshAsync();
        verify(concreteSnmpIntegrationService).refreshAsync();
        verify(zkBioRefreshOrchestrationService).refreshMonitoringAndAttendanceAsync();
        verify(cameraIntegrationService).refreshAsync();
        verify(snapshotPublicationService).publishMonitoringSnapshots(List.of(
                MonitoringSourceType.ZABBIX,
                MonitoringSourceType.SNMP,
                MonitoringSourceType.ZKBIO
        ));
        verify(snapshotPublicationService).publishZkBioSnapshots();
    }

    @Test
    void controllerCollectAllPublishesOnlyAfterSequentialZkBioPipelineCompletes() {
        MonitoringController controller = new MonitoringController(
                monitoringAggregationService,
                sourceAvailabilityService,
                integrationServiceRegistry,
                zkBioRefreshOrchestrationService,
                snapshotPublicationService,
                snmpInterfaceService,
                monitoringFreshnessService
        );

        when(integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX)).thenReturn(concreteZabbixIntegrationService);
        when(integrationServiceRegistry.getRequired(MonitoringSourceType.SNMP)).thenReturn(concreteSnmpIntegrationService);
        when(integrationServiceRegistry.getRequired(MonitoringSourceType.CAMERA)).thenReturn(cameraIntegrationService);
        when(concreteZabbixIntegrationService.refreshAsync()).thenReturn(Mono.empty());
        when(concreteSnmpIntegrationService.refreshAsync()).thenReturn(Mono.empty());
        when(cameraIntegrationService.refreshAsync()).thenReturn(Mono.empty());
        when(zkBioRefreshOrchestrationService.refreshMonitoringAndAttendanceAsync()).thenReturn(Mono.empty());

        controller.collectAll().block();

        InOrder inOrder = inOrder(
                concreteZabbixIntegrationService,
                concreteSnmpIntegrationService,
                zkBioRefreshOrchestrationService,
                cameraIntegrationService,
                snapshotPublicationService
        );
        inOrder.verify(concreteZabbixIntegrationService).refreshAsync();
        inOrder.verify(concreteSnmpIntegrationService).refreshAsync();
        inOrder.verify(zkBioRefreshOrchestrationService).refreshMonitoringAndAttendanceAsync();
        inOrder.verify(cameraIntegrationService).refreshAsync();
        inOrder.verify(snapshotPublicationService).publishMonitoringSnapshots(List.of(
                MonitoringSourceType.ZABBIX,
                MonitoringSourceType.SNMP,
                MonitoringSourceType.ZKBIO
        ));
        inOrder.verify(snapshotPublicationService).publishZkBioSnapshots();
    }

    @Test
    void startupPublishesZkBioOnlyAfterOrchestrationPipelineCompletes() {
        MonitoringStartup startup = new MonitoringStartup(
                integrationServiceRegistry,
                zkBioRefreshOrchestrationService,
                snapshotPublicationService
        );
        Sinks.Empty<Void> zkbioSink = Sinks.empty();

        when(integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX)).thenReturn(zabbixIntegrationService);
        when(integrationServiceRegistry.getRequired(MonitoringSourceType.SNMP)).thenReturn(snmpIntegrationService);
        when(integrationServiceRegistry.getRequired(MonitoringSourceType.CAMERA)).thenReturn(cameraIntegrationService);
        when(zabbixIntegrationService.refreshAsync()).thenReturn(Mono.empty());
        when(snmpIntegrationService.refreshAsync()).thenReturn(Mono.empty());
        when(cameraIntegrationService.refreshAsync()).thenReturn(Mono.empty());
        when(zkBioRefreshOrchestrationService.refreshMonitoringAndAttendanceAsync()).thenReturn(zkbioSink.asMono());

        startup.warmupInitialSnapshots();

        verifyNoInteractions(snapshotPublicationService);

        zkbioSink.tryEmitEmpty();

        InOrder inOrder = inOrder(zkBioRefreshOrchestrationService, snapshotPublicationService);
        inOrder.verify(zkBioRefreshOrchestrationService).refreshMonitoringAndAttendanceAsync();
        inOrder.verify(snapshotPublicationService).publishMonitoringSnapshots(List.of(
                MonitoringSourceType.ZABBIX,
                MonitoringSourceType.SNMP,
                MonitoringSourceType.ZKBIO
        ));
        inOrder.verify(snapshotPublicationService).publishZkBioSnapshots();
    }
}
