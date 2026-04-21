package tn.iteam;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tn.iteam.integration.CameraIntegrationService;
import tn.iteam.integration.IntegrationService;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.websocket.MonitoringWebSocketPublisher;
import tn.iteam.websocket.ZkBioWebSocketPublisher;

/**
 * Single bootstrap entry point for monitoring warmup.
 *
 * Startup is responsible only for the initial snapshot hydration and the first
 * websocket publication after the Spring context is ready.
 *
 * Periodic refreshes remain owned by the source-specific schedulers.
 */
@Slf4j
@Component
public class MonitoringStartup {

    private final IntegrationService zabbixIntegrationService;
    private final IntegrationService observiumIntegrationService;
    private final IntegrationService zkBioIntegrationService;
    private final CameraIntegrationService cameraIntegrationService;
    private final MonitoringWebSocketPublisher monitoringWebSocketPublisher;
    private final ZkBioWebSocketPublisher zkBioWebSocketPublisher;

    public MonitoringStartup(
            @Qualifier("zabbixIntegrationService") IntegrationService zabbixIntegrationService,
            @Qualifier("observiumIntegrationService") IntegrationService observiumIntegrationService,
            @Qualifier("zkBioIntegrationService") IntegrationService zkBioIntegrationService,
            CameraIntegrationService cameraIntegrationService,
            MonitoringWebSocketPublisher monitoringWebSocketPublisher,
            ZkBioWebSocketPublisher zkBioWebSocketPublisher
    ) {
        this.zabbixIntegrationService = zabbixIntegrationService;
        this.observiumIntegrationService = observiumIntegrationService;
        this.zkBioIntegrationService = zkBioIntegrationService;
        this.cameraIntegrationService = cameraIntegrationService;
        this.monitoringWebSocketPublisher = monitoringWebSocketPublisher;
        this.zkBioWebSocketPublisher = zkBioWebSocketPublisher;
    }

    @PostConstruct
    public void warmupInitialSnapshots() {
        refreshSafely("Zabbix", () -> {
            zabbixIntegrationService.refresh();
            monitoringWebSocketPublisher.publishProblemsFromSnapshot(MonitoringSourceType.ZABBIX);
            monitoringWebSocketPublisher.publishMetricsFromSnapshot(MonitoringSourceType.ZABBIX);
        });
        refreshSafely("Observium", () -> {
            observiumIntegrationService.refresh();
            monitoringWebSocketPublisher.publishProblemsFromSnapshot(MonitoringSourceType.OBSERVIUM);
            monitoringWebSocketPublisher.publishMetricsFromSnapshot(MonitoringSourceType.OBSERVIUM);
        });
        refreshSafely("ZKBio", () -> {
            zkBioIntegrationService.refresh();
            zkBioIntegrationService.refreshAttendance();
            monitoringWebSocketPublisher.publishProblemsFromSnapshot(MonitoringSourceType.ZKBIO);
            monitoringWebSocketPublisher.publishMetricsFromSnapshot(MonitoringSourceType.ZKBIO);
            zkBioWebSocketPublisher.publishAttendanceFromSnapshot();
            zkBioWebSocketPublisher.publishDevicesFromSnapshot();
            zkBioWebSocketPublisher.publishStatusFromSnapshot();
        });
        refreshSafely("Camera", cameraIntegrationService::refresh);
    }

    private void refreshSafely(String source, Runnable action) {
        try {
            action.run();
        } catch (Exception exception) {
            log.warn("Startup refresh for {} failed: {}", source, exception.getMessage());
        }
    }
}
